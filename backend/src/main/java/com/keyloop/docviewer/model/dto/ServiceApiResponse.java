package com.keyloop.docviewer.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO representing the response from the external Service System API.
 * Field names match the Service API JSON contract exactly.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceApiResponse {

    private List<ServiceRecord> records;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceRecord {
        private String recordId;
        private String description;
        private String category;
        /** ISO-8601 timestamp string */
        private String serviceDate;
        private String documentUrl;
    }
}

