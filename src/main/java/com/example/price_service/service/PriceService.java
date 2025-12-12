package com.example.price_service.service;

import com.example.price_service.model.PriceRecord;

import java.util.List;
import java.util.Optional;

public interface PriceService {

    // Consumer Methods
    /**
     * Retrieves the last price record for a given id based on asOf time.
     */
    Optional<PriceRecord> getLastPrice(String id); //

    /**
     * Retrieves the last prices of all the records.
     */
    List<PriceRecord> getAllPrices();

    // Producer Methods

    List<String> getPendingBatches();

    /**
     * Uploads a chunk of records to an active batch.
     */
    List<String>  uploadChunk(List<PriceRecord> records); //

    /**
     * Completes the batch, making records available atomically.
     */
    void completeBatch(String batchId); //

    /**
     * Cancels the batch, discarding uploaded records.
     */
    void cancelBatch(String batchId); //
}
