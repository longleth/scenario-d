package com.keyloop.docviewer.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Top-level API response for the unified document search endpoint. */
@Data
@Builder
public class DocumentsResponse {

    private String vin;
    private List<DocumentDto> documents;
    private int totalCount;

    /** Per-source fetch status — useful for diagnosing partial failures. */
    private Map<String, SourceStatus> sources;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceStatus {
        /** OK | ERROR */
        private String status;
        private int count;
        /** Populated only when status is ERROR */
        private String errorMessage;
    }
}

