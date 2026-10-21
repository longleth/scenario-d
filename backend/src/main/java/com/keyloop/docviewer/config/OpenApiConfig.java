package com.keyloop.docviewer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Unified Document Viewer API")
                        .description("""
                                Aggregates vehicle-related documents from the Sales System and the Service System
                                into a single unified view, identified by VIN.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Keyloop").url("https://keyloop.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local / Docker")));
    }
}

