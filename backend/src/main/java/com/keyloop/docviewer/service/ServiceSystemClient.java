package com.keyloop.docviewer.service;

import com.keyloop.docviewer.model.dto.ServiceApiResponse;
import com.keyloop.docviewer.model.dto.ServiceApiResponse.ServiceRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ServiceSystemClient {

    private final WebClient webClient;

    public ServiceSystemClient(@Qualifier("serviceWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<ServiceRecord>> fetchDocuments(String vin) {
        log.info("Calling Service API for VIN={}", vin);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/documents")
                        .queryParam("vin", vin)
                        .build())
                .retrieve()
                .bodyToMono(ServiceApiResponse.class)
                .map(resp -> {
                    List<ServiceRecord> records = resp.getRecords();
                    return records != null
                            ? records
                            : Collections.<ServiceRecord>emptyList();
                })
                .doOnSuccess(docs -> log.info(
                        "Service API responded with {} record(s) for VIN={}",
                        docs.size(), vin))
                .doOnError(e -> log.error(
                        "Service API error for VIN={}: {}", vin, e.getMessage()));
    }
}