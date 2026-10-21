package com.keyloop.docviewer.service;

import com.keyloop.docviewer.model.dto.SalesApiResponse;
import com.keyloop.docviewer.model.dto.SalesApiResponse.SalesDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class SalesSystemClient {

    private final WebClient webClient;

    public SalesSystemClient(@Qualifier("salesWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<SalesDocument>> fetchDocuments(String vin) {
        log.info("Calling Sales API for VIN={}", vin);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/documents")
                        .queryParam("vin", vin)
                        .build())
                .retrieve()
                .bodyToMono(SalesApiResponse.class)
                .map(resp -> {
                    List<SalesDocument> documents = resp.getDocuments();
                    return documents != null
                            ? documents
                            : Collections.<SalesDocument>emptyList();
                })
                .doOnSuccess(docs -> log.info(
                        "Sales API responded with {} document(s) for VIN={}",
                        docs.size(), vin))
                .doOnError(e -> log.error(
                        "Sales API error for VIN={}: {}", vin, e.getMessage()));
    }
}