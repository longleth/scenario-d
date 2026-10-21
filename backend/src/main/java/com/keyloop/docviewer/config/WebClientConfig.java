package com.keyloop.docviewer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Creates named WebClient beans for each external downstream system.
 * Using separate beans allows independent configuration (timeouts, headers, etc.)
 * per target without coupling the service layer to base-URL configuration.
 */
@Configuration
public class WebClientConfig {

    @Bean("salesWebClient")
    public WebClient salesWebClient(
            @Value("${external.sales-api.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("serviceWebClient")
    public WebClient serviceWebClient(
            @Value("${external.service-api.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}

