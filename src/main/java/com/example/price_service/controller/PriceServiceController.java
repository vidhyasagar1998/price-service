package com.example.price_service.controller;

import com.example.price_service.model.PriceRecord;
import com.example.price_service.service.PriceService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Price API", description = "Operations for Producers to publish prices and Consumers to read them")
public class PriceServiceController {

    private final PriceService priceService;

    public PriceServiceController(PriceService priceService) {
        this.priceService = priceService;
    }

    // --- Consumer Endpoint ---

    @Operation(summary = "Get Last Price", description = "Retrieves the last price record for a given instrument ID. The last value is determined by the asOf time.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Price record found",
                    content = { @Content(mediaType = "application/json", schema = @Schema(implementation = PriceRecord.class)) }),
            @ApiResponse(responseCode = "404", description = "Instrument ID not found", content = @Content)
    })
    @GetMapping("/prices/{id}")
    public ResponseEntity<?> getLastPrice(
            @Parameter(description = "Instrument Identifier (e.g., AAPL)", required = true)
            @PathVariable String id) {
        return priceService.getLastPrice(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get All Prices", description = "Retrieves a list of all available price records in the system.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list",
                    content = { @Content(mediaType = "application/json", schema = @Schema(implementation = PriceRecord.class)) })
    })
    @GetMapping("/prices") // Maps to GET /api/prices
    public ResponseEntity<?> getAllPrices() {
        List<PriceRecord> prices = priceService.getAllPrices();

        if (prices.isEmpty()) {
            return ResponseEntity.noContent().build(); // Returns ResponseEntity<Void>
        }

        return ResponseEntity.ok(prices); // Returns ResponseEntity<List<PriceRecord>>
    }

    // --- Producer Endpoints ---

    @Operation(
            summary = "Get Pending Batches",
            description = "Retrieves a list of all batch IDs that are currently staged but not yet completed."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved pending batches",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = String.class, type = "array") // Explicitly define List<String>
                    )
            ),
            @ApiResponse(
                    responseCode = "204",
                    description = "No pending batches found",
                    content = @Content // Empty content for 204
            )
    })
    @GetMapping("/batches")
    public ResponseEntity<?> getStagedBatches() {
        List<String> pendingBatches = priceService.getPendingBatches();

        if (pendingBatches.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(pendingBatches);
    }

    @Operation(
            summary = "Upload & Auto-Batch",
            description = "Uploads a list of prices. The server automatically splits them into batches of 5 and returns the generated Batch IDs."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Batches created successfully. Returns list of Batch IDs.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = List.class))
            ),
            @ApiResponse(responseCode = "400", description = "Input list is empty or null")
    })
    @PostMapping("/batch/upload") // changed from PUT /batch/{id}
    public ResponseEntity<List<String>> uploadChunk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of PriceRecords. Will be split into chunks of 5.",
                    required = true
            )
            @RequestBody List<PriceRecord> records) {

        // Input Validation
        if (records == null || records.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Call Service
        List<String> generatedBatchIds = priceService.uploadChunk(records);

        // Return 201 Created with the list of new IDs
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(generatedBatchIds);
    }

    @Operation(summary = "Complete Batch", description = "Completes the batch run. All prices become available to consumers at the same time[cite: 10].")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Batch completion accepted and processed"),
            @ApiResponse(responseCode = "400", description = "Invalid Batch ID or batch already processed")
    })
    @PostMapping("/batch/{batchId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void completeBatch(
            @Parameter(description = "The unique Batch ID to complete")
            @PathVariable String batchId) {
        priceService.completeBatch(batchId);
    }

    @Operation(summary = "Cancel Batch", description = "Cancels a batch run. Uploaded records are discarded[cite: 11].")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Batch cancelled and data discarded"),
            @ApiResponse(responseCode = "400", description = "Invalid Batch ID")
    })
    @DeleteMapping("/batch/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelBatch(
            @Parameter(description = "The unique Batch ID to cancel")
            @PathVariable String batchId) {
        priceService.cancelBatch(batchId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden // Hides this technical handler from the main API documentation list if desired, or can be documented as a generic error
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}