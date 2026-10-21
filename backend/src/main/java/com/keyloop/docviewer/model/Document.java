package com.keyloop.docviewer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a vehicle document persisted from either the
 * Sales System or the Service System.
 */
@Entity
@Table(
    name = "documents",
    indexes = {
        @Index(name = "idx_documents_vin", columnList = "vin"),
        @Index(name = "idx_documents_vin_source", columnList = "vin, source")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    private UUID id;

    /** 17-character ISO 3779 Vehicle Identification Number */
    @Column(nullable = false, length = 17)
    private String vin;

    @Column(length = 500)
    private String title;

    /** Document category, e.g. CONTRACT, INVOICE, MAINTENANCE, INSPECTION */
    @Column(length = 100)
    private String type;

    /** Origin system: SALES or SERVICE */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentSource source;

    /** Original creation timestamp from the source system */
    private Instant createdAt;

    /** URL to download/view the document from the source system */
    @Column(columnDefinition = "TEXT")
    private String url;

    /** Timestamp when this record was last fetched from the external API */
    @Column(nullable = false)
    private Instant fetchedAt;
}

