package com.example.price_service.service;

import com.example.price_service.model.PriceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPriceServiceTest {

    private InMemoryPriceService priceService;

    @BeforeEach
    void setUp() {
        // We instantiate the service directly to test it in isolation (Unit Test)
        priceService = new InMemoryPriceService();
    }

    @Test
    @DisplayName("Should load initial dummy data after initialization")
    void testInitData() {
        priceService.initData();

        List<PriceRecord> allPrices = priceService.getAllPrices();

        // We expect the 15 records defined in your getInitialDummyData()
        assertThat(allPrices).hasSize(15);

        // Verify a specific record exists
        Optional<PriceRecord> reliance = priceService.getLastPrice("RELIANCE");
        assertThat(reliance).isPresent();
        //assertThat(reliance.get().payload().get("sector")).isEqualTo("Oil & Gas");
    }

    @Test
    @DisplayName("getLastPrice should return empty for unknown instrument")
    void testGetLastPrice_NotFound() {
        Optional<PriceRecord> result = priceService.getLastPrice("UNKNOWN_TICKER");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("UploadChunk should split large lists into batches of 5")
    void testUploadChunk_BatchSplitting() {
        // Create 12 dummy records
        List<PriceRecord> inputRecords = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < 12; i++) {
            inputRecords.add(createTestRecord("TICKER_" + i, 100.0, now));
        }

        // Upload
        List<String> batchIds = priceService.uploadChunk(inputRecords);

        // Validation: 12 items should create 3 batches (5 + 5 + 2)
        assertThat(batchIds).hasSize(3);
        assertThat(priceService.getPendingBatches()).containsExactlyInAnyOrderElementsOf(batchIds);
    }

    @Test
    @DisplayName("CompleteBatch should commit data to main store")
    void testCompleteBatch_Success() {
        // Setup a batch
        PriceRecord record = createTestRecord("TEST_STOCK", 500.0, ZonedDateTime.now());
        List<String> batchIds = priceService.uploadChunk(List.of(record));
        String batchId = batchIds.get(0);

        // Pre-check: Main store is empty (aside from init if called, but we didn't call init here)
        assertThat(priceService.getLastPrice("TEST_STOCK")).isEmpty();

        // Execute
        priceService.completeBatch(batchId);

        // Post-check: Data exists
        assertThat(priceService.getLastPrice("TEST_STOCK")).isPresent();
        assertThat(priceService.getPendingBatches()).doesNotContain(batchId);
    }

    @Test
    @DisplayName("CompleteBatch should throw exception for invalid Batch ID")
    void testCompleteBatch_InvalidId() {
        assertThatThrownBy(() -> priceService.completeBatch("INVALID_UUID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch ID not found");
    }

    @Test
    @DisplayName("Merge Logic: Should UPDATE if new record is NEWER")
    void testMerge_NewerUpdate() {
        ZonedDateTime time1 = ZonedDateTime.now().minusHours(1);
        ZonedDateTime time2 = ZonedDateTime.now();

        // 1. Upload and complete older record
        PriceRecord oldRecord = createTestRecord("VOLTAS", 100.0, time1);
        String batch1 = priceService.uploadChunk(List.of(oldRecord)).get(0);
        priceService.completeBatch(batch1);

        // 2. Upload and complete newer record
        PriceRecord newRecord = createTestRecord("VOLTAS", 150.0, time2);
        String batch2 = priceService.uploadChunk(List.of(newRecord)).get(0);
        priceService.completeBatch(batch2);

        // Verify: Price should be 150.0
        PriceRecord result = priceService.getLastPrice("VOLTAS").get();
        assertThat(result.asOf()).isEqualTo(time2);
        //assertThat(result.payload().get("price")).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Merge Logic: Should IGNORE if new record is OLDER (Stale Data)")
    void testMerge_StaleUpdate() {
        ZonedDateTime newerTime = ZonedDateTime.now();
        ZonedDateTime olderTime = ZonedDateTime.now().minusHours(1);

        // 1. Upload and complete newer record FIRST
        PriceRecord newRecord = createTestRecord("INFY", 2000.0, newerTime);
        String batch1 = priceService.uploadChunk(List.of(newRecord)).get(0);
        priceService.completeBatch(batch1);

        // 2. Upload and complete older record LATER (Simulating delayed message)
        PriceRecord oldRecord = createTestRecord("INFY", 1000.0, olderTime);
        String batch2 = priceService.uploadChunk(List.of(oldRecord)).get(0);
        priceService.completeBatch(batch2);

        // Verify: Price should remain 2000.0 (The newer one)
        PriceRecord result = priceService.getLastPrice("INFY").get();
        assertThat(result.asOf()).isEqualTo(newerTime);
        //assertThat(result.payload().get("price")).isEqualTo(2000.0);
    }

    @Test
    @DisplayName("CancelBatch should remove data from staging without committing")
    void testCancelBatch() {
        PriceRecord record = createTestRecord("CANCEL_ME", 100.0, ZonedDateTime.now());
        List<String> batchIds = priceService.uploadChunk(List.of(record));
        String batchId = batchIds.get(0);

        // Verify it is staged
        assertThat(priceService.getPendingBatches()).contains(batchId);

        // Cancel
        priceService.cancelBatch(batchId);

        // Verify removed from staging
        assertThat(priceService.getPendingBatches()).doesNotContain(batchId);

        // Verify NOT in main store
        assertThat(priceService.getLastPrice("CANCEL_ME")).isEmpty();
    }

    // --- Helper Methods ---

    private PriceRecord createTestRecord(String id, double price, ZonedDateTime asOf) {
        // Assuming PriceRecord constructor matches your code: (id, asOf, payload)
        return new PriceRecord(id, asOf, Map.of(
                "price", price,
                "currency", "INR",
                "sector", "Test Sector"
        ));
    }
}