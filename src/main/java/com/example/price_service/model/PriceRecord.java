package com.example.price_service.model;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Represents the price data structure.
 * Using Java Records for immutability and conciseness.
 */
public record PriceRecord(
        String id,          //
        ZonedDateTime asOf,       //
        Object payload      // Flexible data structure
) {}