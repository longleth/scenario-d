package com.keyloop.docviewer.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO representing the response from the external Sales System API.
 * Field names match the Sales API JSON contract exactly.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesApiResponse {

    private List<SalesDocument> documents;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalesDocument {
        private String id;
        private String title;
        private String documentType;
        /** ISO-8601 timestamp string */
        private String date;
        private String downloadUrl;
    }
}

