package com.orbita.orders.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Orders Service → consumed by Payments Service.
 * Stored in outbox table and relayed by OutboxPoller.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentRequestedEvent {
    private UUID eventId;
    private UUID orderId;
    private String userId;
    private Long amount;
    private Instant occurredAt;

    public static OrderPaymentRequestedEvent of(UUID orderId, String userId, long amount) {
        return new OrderPaymentRequestedEvent(UUID.randomUUID(), orderId, userId, amount, Instant.now());
    }
}
