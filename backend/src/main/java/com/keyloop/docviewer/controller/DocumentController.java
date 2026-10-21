package com.keyloop.docviewer.controller;

import com.keyloop.docviewer.model.dto.DocumentsResponse;
import com.keyloop.docviewer.service.DocumentAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Documents", description = "Unified vehicle document search")
public class DocumentController {

    private final DocumentAggregatorService aggregatorService;

    @GetMapping
    @Operation(summary = "Get all documents for a vehicle by VIN",
               description = "Queries both the Sales System and the Service System in parallel and returns a unified document list.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful aggregation (may include partial data if one system is unavailable)"),
        @ApiResponse(responseCode = "400", description = "Invalid or missing VIN", content = @Content(schema = @Schema())),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(schema = @Schema()))
    })
    public ResponseEntity<DocumentsResponse> getDocumentsByVin(
            @Parameter(description = "17-character ISO 3779 Vehicle Identification Number", required = true, example = "1HGCM82633A123456")
            @RequestParam
            @NotBlank(message = "VIN must not be blank")
            @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$",
                     message = "VIN must be exactly 17 characters (alphanumeric, excluding I, O, Q)")
            String vin) {

        log.info("GET /api/v1/documents?vin={}", vin);
        DocumentsResponse response = aggregatorService.getDocumentsForVin(vin.toUpperCase());
        return ResponseEntity.ok(response);
    }
}

