package com.keyloop.docviewer.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Unified document representation returned to API clients. */
@Data
@Builder
public class DocumentDto {
    private UUID id;
    private String title;
    private String type;
    /** SALES or SERVICE */
    private String source;
    private Instant createdAt;
    private String url;
}

