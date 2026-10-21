package com.keyloop.docviewer.service;

import com.keyloop.docviewer.model.dto.DocumentsResponse;
import com.keyloop.docviewer.model.dto.SalesApiResponse;
import com.keyloop.docviewer.model.dto.ServiceApiResponse;
import com.keyloop.docviewer.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAggregatorService")
class DocumentAggregatorServiceTest {

    @Mock
    private SalesSystemClient salesSystemClient;

    @Mock
    private ServiceSystemClient serviceSystemClient;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentAggregatorService service;

    private static final String VIN = "1HGCM82633A123456";

    @BeforeEach
    void setUp() {
        // saveAll returns its input — simulates Hibernate merge
        when(documentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should aggregate documents from both sources when both APIs succeed")
    void shouldAggregateFromBothSources() {
        when(salesSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(salesDoc("s1", "Purchase Agreement", "CONTRACT", "2024-01-15T10:30:00Z"))));
        when(serviceSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(serviceRecord("sv1", "Oil Change", "MAINTENANCE", "2024-06-20T09:00:00Z"))));

        DocumentsResponse response = service.getDocumentsForVin(VIN);

        assertThat(response.getVin()).isEqualTo(VIN);
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getDocuments()).hasSize(2);
        assertThat(response.getDocuments()).anyMatch(d -> "SALES".equals(d.getSource()));
        assertThat(response.getDocuments()).anyMatch(d -> "SERVICE".equals(d.getSource()));
        assertThat(response.getSources().get("SALES").getStatus()).isEqualTo("OK");
        assertThat(response.getSources().get("SERVICE").getStatus()).isEqualTo("OK");
        assertThat(response.getSources().get("SALES").getCount()).isEqualTo(1);
        assertThat(response.getSources().get("SERVICE").getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should sort aggregated documents by createdAt descending")
    void shouldSortByCreatedAtDescending() {
        when(salesSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(salesDoc("s1", "Old Doc", "CONTRACT", "2023-01-01T00:00:00Z"))));
        when(serviceSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(serviceRecord("sv1", "New Record", "MAINTENANCE", "2024-06-20T09:00:00Z"))));

        DocumentsResponse response = service.getDocumentsForVin(VIN);

        assertThat(response.getDocuments().get(0).getCreatedAt())
                .isAfter(response.getDocuments().get(1).getCreatedAt());
    }

    // ── Partial failure scenarios ──────────────────────────────────────────────

    @Test
    @DisplayName("should return partial result with ERROR status when Sales API fails")
    void shouldReturnPartialResultWhenSalesApiFails() {
        when(salesSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.error(new RuntimeException("Connection timeout")));
        when(serviceSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(serviceRecord("sv1", "Oil Change", "MAINTENANCE", null))));

        DocumentsResponse response = service.getDocumentsForVin(VIN);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSources().get("SALES").getStatus()).isEqualTo("ERROR");
        assertThat(response.getSources().get("SALES").getErrorMessage()).contains("Connection timeout");
        assertThat(response.getSources().get("SERVICE").getStatus()).isEqualTo("OK");
    }

    @Test
    @DisplayName("should return partial result with ERROR status when Service API fails")
    void shouldReturnPartialResultWhenServiceApiFails() {
        when(salesSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(salesDoc("s1", "Purchase Agreement", "CONTRACT", null))));
        when(serviceSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.error(new RuntimeException("Service API down")));

        DocumentsResponse response = service.getDocumentsForVin(VIN);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getSources().get("SALES").getStatus()).isEqualTo("OK");
        assertThat(response.getSources().get("SERVICE").getStatus()).isEqualTo("ERROR");
        assertThat(response.getSources().get("SERVICE").getErrorMessage()).contains("Service API down");
    }

    @Test
    @DisplayName("should return empty document list when both APIs fail")
    void shouldReturnEmptyWhenBothFail() {
        when(salesSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.error(new RuntimeException("Sales API down")));
        when(serviceSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.error(new RuntimeException("Service API down")));

        DocumentsResponse response = service.getDocumentsForVin(VIN);

        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getDocuments()).isEmpty();
        assertThat(response.getSources().get("SALES").getStatus()).isEqualTo("ERROR");
        assertThat(response.getSources().get("SERVICE").getStatus()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("should persist aggregated documents to the database")
    void shouldPersistDocumentsToDatabase() {
        when(salesSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(salesDoc("s1", "Purchase Agreement", "CONTRACT", null))));
        when(serviceSystemClient.fetchDocuments(VIN))
                .thenReturn(Mono.just(List.of(serviceRecord("sv1", "Oil Change", "MAINTENANCE", null))));

        service.getDocumentsForVin(VIN);

        verify(documentRepository).saveAll(argThat(docs -> {
            var list = (List<?>) docs;
            return list.size() == 2;
        }));
    }

    @Test
    @DisplayName("should handle empty responses from both APIs gracefully")
    void shouldHandleEmptyResponses() {
        when(salesSystemClient.fetchDocuments(VIN)).thenReturn(Mono.just(List.of()));
        when(serviceSystemClient.fetchDocuments(VIN)).thenReturn(Mono.just(List.of()));

        DocumentsResponse response = service.getDocumentsForVin(VIN);

        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getSources().get("SALES").getStatus()).isEqualTo("OK");
        assertThat(response.getSources().get("SERVICE").getStatus()).isEqualTo("OK");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private SalesApiResponse.SalesDocument salesDoc(String id, String title, String type, String date) {
        var doc = new SalesApiResponse.SalesDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setDocumentType(type);
        doc.setDate(date);
        doc.setDownloadUrl("https://sales.example.com/" + id);
        return doc;
    }

    private ServiceApiResponse.ServiceRecord serviceRecord(String id, String desc, String category, String date) {
        var rec = new ServiceApiResponse.ServiceRecord();
        rec.setRecordId(id);
        rec.setDescription(desc);
        rec.setCategory(category);
        rec.setServiceDate(date);
        rec.setDocumentUrl("https://service.example.com/" + id);
        return rec;
    }
}

