package com.keyloop.docviewer.repository;

import com.keyloop.docviewer.model.Document;
import com.keyloop.docviewer.model.DocumentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Retrieves all documents for a given VIN sorted by createdAt descending.
     * Used when returning cached results.
     */
    List<Document> findByVinOrderByCreatedAtDesc(String vin);

    /** Checks existence to support idempotent upsert logic if required. */
    boolean existsByIdAndSource(UUID id, DocumentSource source);
}

