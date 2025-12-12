package com.example.price_service.service;

import com.example.price_service.model.PriceRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class InMemoryPriceService implements PriceService {

    // Main storage: Instrument ID -> PriceRecord
    // Guarded by ReadWriteLock to ensure atomic batch updates.
    private final Map<String, PriceRecord> mainStore = new HashMap<>();

    // Staging area: Batch ID -> List of uploaded records
    // Concurrent map to handle multiple producers starting batches simultaneously.
    private final Map<String, List<PriceRecord>> batchStaging = new ConcurrentHashMap<>();

    // Lock to control access to mainStore
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @PostConstruct
    public void initData() {
        System.out.println("Loading Nifty data into PriceService...");
        List<PriceRecord> records = getInitialDummyData();
        records.forEach(r -> mainStore.put(r.id(), r));
        System.out.println("Initialized " + mainStore.size() + " Nifty records.");
    }

    @Override
    public Optional<PriceRecord> getLastPrice(String id) {
        lock.readLock().lock(); // Allow concurrent reads, block if a batch is committing
        try {
            return Optional.ofNullable(mainStore.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<PriceRecord> getAllPrices() {
        lock.readLock().lock();  // Block if a batch is currently committing [cite: 10]
        try {
            // Return a copy of the values to avoid ConcurrentModificationException
            // if the map changes after we release the lock.
            return new ArrayList<>(mainStore.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<String> getPendingBatches() {
        return new ArrayList<>(batchStaging.keySet());
    }

    @Override
    public List<String> uploadChunk(List<PriceRecord> records) {
        List<String> createdBatchIds = new ArrayList<>();
        int batchSize = 5; // Requirement: Divide into batches of 5

        for (int i = 0; i < records.size(); i += batchSize) {
            String batchId;

            // Production Standard: Defensive check to ensure global uniqueness in the map
            do {
                batchId = UUID.randomUUID().toString();
            } while (batchStaging.containsKey(batchId));

            // Create the sub-list (safe copy)
            int end = Math.min(records.size(), i + batchSize);
            List<PriceRecord> chunk = new ArrayList<>(records.subList(i, end));

            // Store directly into batchStaging
            batchStaging.put(batchId, chunk);
            createdBatchIds.add(batchId);
        }

        // Return the list of unique IDs so the client can complete them
        return createdBatchIds;
    }

    @Override
    public void completeBatch(String batchId) {
        List<PriceRecord> stagedRecords = batchStaging.remove(batchId);
        if (stagedRecords == null) {
            throw new IllegalArgumentException("Batch ID not found or already processed: " + batchId);
        }

        lock.writeLock().lock(); // Block readers and other writers
        try {
            for (PriceRecord record : stagedRecords) {
                mergeRecord(record);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void cancelBatch(String batchId) {
        // Batch runs which are cancelled can be discarded.
        // Simply removing it from the staging map discards the data.
        if (batchStaging.remove(batchId) == null) {
            throw new IllegalArgumentException("Batch ID not found: " + batchId);
        }
    }

    /**
     * Merges a record into the main store based on business rule:
     * Last value is determined by the asOf time .
     */
    private void mergeRecord(PriceRecord newRecord) {
        mainStore.compute(newRecord.id(), (key, existingRecord) -> {
            if (existingRecord == null) {
                return newRecord;
            }
            // Only update if the new record is newer than the existing one
            if (newRecord.asOf().isAfter(existingRecord.asOf())) {
                return newRecord;
            }
            return existingRecord; // Keep existing
        });
    }

    // Helper method to construct PriceRecord
    private PriceRecord createRecord(String ticker, double price, String sector, ZonedDateTime asOf) {
        return new PriceRecord(ticker, asOf, Map.of(
                "price", price,
                "currency", "INR",
                "sector", sector
        ));
    }

    private List<PriceRecord> getInitialDummyData(){
        ZonedDateTime nowIst = Instant.now().atZone(ZoneId.of("Asia/Kolkata"));

        // Helper to keep code clean
        return List.of(
                createRecord("RELIANCE", 1546.70, "Oil & Gas", nowIst),
                createRecord("TCS", 3212.80, "IT Services", nowIst),
                createRecord("HDFCBANK", 1001.20, "Private Bank", nowIst),
                createRecord("ICICIBANK", 1365.60, "Private Bank", nowIst),
                createRecord("BHARTIARTL", 2067.00, "Telecom", nowIst),
                createRecord("INFY", 1594.60, "IT Services", nowIst),
                createRecord("ITC", 400.35, "FMCG", nowIst),
                createRecord("SBIN", 961.00, "Public Bank", nowIst),
                createRecord("LT", 4081.10, "Construction", nowIst),
                createRecord("HINDUNILVR", 2259.20, "FMCG", nowIst),
                createRecord("AXISBANK", 1287.80, "Private Bank", nowIst),
                createRecord("KOTAKBANK", 2181.40, "Private Bank", nowIst),
                createRecord("MARUTI", 16493.00, "Automobile", nowIst),
                createRecord("ULTRACEMCO", 11714.00, "Cement", nowIst),
                createRecord("ASIANPAINT", 2804.50, "Paints", nowIst)
        );
    }
}