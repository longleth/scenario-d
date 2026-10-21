package com.keyloop.docviewer.service;

import com.keyloop.docviewer.model.Document;
import com.keyloop.docviewer.model.DocumentSource;
import com.keyloop.docviewer.model.dto.*;
import com.keyloop.docviewer.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core orchestration service.
 *
 * <p>Fires parallel requests to the Sales System and the Service System using
 * {@link Mono#zip}, applies a per-call timeout, and gracefully degrades to a
 * partial result if either upstream fails.  Results are persisted (upserted)
 * in the database so repeated queries for the same VIN can be served faster.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAggregatorService {

    /** Maximum time to wait for a single external API response. */
    static final Duration EXTERNAL_API_TIMEOUT = Duration.ofSeconds(5);

    private final SalesSystemClient salesSystemClient;
    private final ServiceSystemClient serviceSystemClient;
    private final DocumentRepository documentRepository;

    @Transactional
    public DocumentsResponse getDocumentsForVin(String vin) {
        log.info("Aggregating documents for VIN={}", vin);

        // Wrap each call in a FetchResult so partial failures don't cancel the zip
        Mono<FetchResult<SalesApiResponse.SalesDocument>> salesMono =
                salesSystemClient.fetchDocuments(vin)
                        .timeout(EXTERNAL_API_TIMEOUT)
                        .map(FetchResult::success)
                        .onErrorResume(e -> {
                            log.warn("Sales API unavailable for VIN={} – {}", vin, e.getMessage());
                            return Mono.just(FetchResult.error(e.getMessage()));
                        });

        Mono<FetchResult<ServiceApiResponse.ServiceRecord>> serviceMono =
                serviceSystemClient.fetchDocuments(vin)
                        .timeout(EXTERNAL_API_TIMEOUT)
                        .map(FetchResult::success)
                        .onErrorResume(e -> {
                            log.warn("Service API unavailable for VIN={} – {}", vin, e.getMessage());
                            return Mono.just(FetchResult.error(e.getMessage()));
                        });

        // Execute both calls in parallel, then merge
        return Mono.zip(salesMono, serviceMono)
                .map(tuple -> buildResponse(vin, tuple.getT1(), tuple.getT2()))
                .block();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private DocumentsResponse buildResponse(
            String vin,
            FetchResult<SalesApiResponse.SalesDocument> salesResult,
            FetchResult<ServiceApiResponse.ServiceRecord> serviceResult) {

        List<Document> salesDocs   = toSalesDocs(vin, salesResult.data());
        List<Document> serviceDocs = toServiceDocs(vin, serviceResult.data());

        // Upsert all documents (saveAll performs merge for existing IDs)
        List<Document> allDocs = Stream.concat(salesDocs.stream(), serviceDocs.stream())
                .collect(Collectors.toList());
        documentRepository.saveAll(allDocs);

        List<DocumentDto> dtos = allDocs.stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(DocumentDto::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Map<String, DocumentsResponse.SourceStatus> sources = new LinkedHashMap<>();
        sources.put("SALES", DocumentsResponse.SourceStatus.builder()
                .status(salesResult.success() ? "OK" : "ERROR")
                .count(salesDocs.size())
                .errorMessage(salesResult.error())
                .build());
        sources.put("SERVICE", DocumentsResponse.SourceStatus.builder()
                .status(serviceResult.success() ? "OK" : "ERROR")
                .count(serviceDocs.size())
                .errorMessage(serviceResult.error())
                .build());

        log.info("Aggregation complete for VIN={}: {} document(s) total (sales={}, service={})",
                vin, dtos.size(), salesDocs.size(), serviceDocs.size());

        return DocumentsResponse.builder()
                .vin(vin)
                .documents(dtos)
                .totalCount(dtos.size())
                .sources(sources)
                .build();
    }

    private List<Document> toSalesDocs(String vin, List<SalesApiResponse.SalesDocument> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .map(sd -> Document.builder()
                        .id(deterministicId("SALES", sd.getId()))
                        .vin(vin)
                        .title(sd.getTitle())
                        .type(sd.getDocumentType())
                        .source(DocumentSource.SALES)
                        .createdAt(parseInstant(sd.getDate()))
                        .url(sd.getDownloadUrl())
                        .fetchedAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

    private List<Document> toServiceDocs(String vin, List<ServiceApiResponse.ServiceRecord> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .map(sr -> Document.builder()
                        .id(deterministicId("SERVICE", sr.getRecordId()))
                        .vin(vin)
                        .title(sr.getDescription())
                        .type(sr.getCategory())
                        .source(DocumentSource.SERVICE)
                        .createdAt(parseInstant(sr.getServiceDate()))
                        .url(sr.getDocumentUrl())
                        .fetchedAt(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

    private DocumentDto toDto(Document doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .type(doc.getType())
                .source(doc.getSource().name())
                .createdAt(doc.getCreatedAt())
                .url(doc.getUrl())
                .build();
    }

    /**
     * Generates a stable UUID from a source prefix + external document ID so
     * re-fetching the same document produces the same DB row (upsert-safe).
     */
    private UUID deterministicId(String sourcePrefix, String externalId) {
        return UUID.nameUUIDFromBytes((sourcePrefix + "-" + externalId).getBytes());
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            log.warn("Could not parse timestamp '{}': {}", raw, e.getMessage());
            return null;
        }
    }

    // ── Inner result wrapper ───────────────────────────────────────────────────

    /**
     * Wraps a successful or failed fetch so that {@link Mono#zip} can always
     * complete even when one upstream fails.
     */
    record FetchResult<T>(List<T> data, boolean success, String error) {

        static <T> FetchResult<T> success(List<T> data) {
            return new FetchResult<>(data, true, null);
        }

        static <T> FetchResult<T> error(String message) {
            return new FetchResult<>(List.of(), false, message);
        }
    }
}

