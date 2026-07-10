package com.orbita.orders.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * Request body for POST /api/v1/orders/orders.
 *
 * Example (ARCHIVE):
 * {
 *   "product_type": "ARCHIVE",
 *   "price": 120,
 *   "payload": { "aoi": "POLYGON(...)", "capture_date": "2024-06-15", "sensor_type": "MSI" }
 * }
 * Example (TASKING):
 * {
 *   "product_type": "TASKING",
 *   "price": 500,
 *   "payload": { "aoi": "POLYGON(...)", "time_window": "2024-07-01/2024-07-15", "sensor_type": "SAR" }
 * }
 * Example (MONITORING):
 * {
 *   "product_type": "MONITORING",
 *   "price": 2000,
 *   "payload": { "aoi": "POLYGON(...)", "cadence": "WEEKLY", "duration_days": 90 }
 * }
 */
public record CreateOrderRequest(
        @NotBlank(message = "product_type is required")
        String product_type,

        @NotNull(message = "price is required")
        @Positive(message = "price must be greater than zero")
        Long price,

        @NotNull(message = "payload is required")
        Map<String, Object> payload
) {}
