package com.example.price_service.controller;

import com.example.price_service.model.PriceRecord;
import com.example.price_service.service.PriceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PriceServiceController.class)
class PriceServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private PriceService priceService;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Consumer Endpoints Tests ---

    @Test
    @DisplayName("GET /api/prices/{id} - Should return 200 and PriceRecord when found")
    void getLastPrice_Found() throws Exception {
        String ticker = "AAPL";
        PriceRecord mockRecord = createDummyRecord(ticker);

        when(priceService.getLastPrice(ticker)).thenReturn(Optional.of(mockRecord));

        mockMvc.perform(get("/api/prices/{id}", ticker))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ticker))
                .andExpect(jsonPath("$.payload.price").value(150.0));
    }

    @Test
    @DisplayName("GET /api/prices/{id} - Should return 404 when not found")
    void getLastPrice_NotFound() throws Exception {
        when(priceService.getLastPrice("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/prices/{id}", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/prices - Should return 200 and list when data exists")
    void getAllPrices_Success() throws Exception {
        List<PriceRecord> records = List.of(createDummyRecord("A"), createDummyRecord("B"));
        when(priceService.getAllPrices()).thenReturn(records);

        mockMvc.perform(get("/api/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("A"));
    }

    @Test
    @DisplayName("GET /api/prices - Should return 204 No Content when empty")
    void getAllPrices_NoContent() throws Exception {
        when(priceService.getAllPrices()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/prices"))
                .andExpect(status().isNoContent());
    }

    // --- Producer Endpoints Tests ---

    @Test
    @DisplayName("GET /api/batches - Should return 200 and list of batch IDs")
    void getStagedBatches_Success() throws Exception {
        List<String> batches = List.of("batch-1", "batch-2");
        when(priceService.getPendingBatches()).thenReturn(batches);

        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("batch-1"));
    }

    @Test
    @DisplayName("GET /api/batches - Should return 204 when no batches pending")
    void getStagedBatches_NoContent() throws Exception {
        when(priceService.getPendingBatches()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/batch/upload - Should return 201 Created and Batch IDs")
    void uploadChunk_Success() throws Exception {
        List<PriceRecord> inputRecords = List.of(createDummyRecord("A"));
        List<String> expectedBatchIds = List.of("uuid-123");

        when(priceService.uploadChunk(any())).thenReturn(expectedBatchIds);

        mockMvc.perform(post("/api/batch/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputRecords)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0]").value("uuid-123"));
    }

    @Test
    @DisplayName("POST /api/batch/upload - Should return 400 Bad Request if list is empty")
    void uploadChunk_BadRequest() throws Exception {
        mockMvc.perform(post("/api/batch/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]")) // Empty JSON array
                .andExpect(status().isBadRequest());

        // Ensure service was never called
        verify(priceService, never()).uploadChunk(any());
    }

    @Test
    @DisplayName("POST /api/batch/{id}/complete - Should return 202 Accepted")
    void completeBatch_Success() throws Exception {
        String batchId = "batch-123";

        // Perform Request
        mockMvc.perform(post("/api/batch/{batchId}/complete", batchId))
                .andExpect(status().isAccepted());

        // Verify service was called exactly once with correct ID
        verify(priceService, times(1)).completeBatch(eq(batchId));
    }

    @Test
    @DisplayName("DELETE /api/batch/{id} - Should return 204 No Content")
    void cancelBatch_Success() throws Exception {
        String batchId = "batch-123";

        mockMvc.perform(delete("/api/batch/{batchId}", batchId))
                .andExpect(status().isNoContent());

        verify(priceService, times(1)).cancelBatch(eq(batchId));
    }

    // --- Exception Handler Test ---

    @Test
    @DisplayName("Should handle IllegalArgumentException and return 400")
    void testGlobalExceptionHandler() throws Exception {
        String invalidBatchId = "invalid-id";

        // Simulate service throwing exception
        doThrow(new IllegalArgumentException("Batch ID not found"))
                .when(priceService).completeBatch(invalidBatchId);

        mockMvc.perform(post("/api/batch/{batchId}/complete", invalidBatchId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Batch ID not found"));
    }

    // --- Helper ---

    private PriceRecord createDummyRecord(String id) {
        // Construct a dummy record matching your record definition
        return new PriceRecord(id, ZonedDateTime.now(), Map.of("price", 150.0));
    }
}