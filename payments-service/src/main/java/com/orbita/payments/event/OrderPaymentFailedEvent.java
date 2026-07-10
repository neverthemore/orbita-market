package com.orbita.payments.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Published to Orders Service when debit fails (e.g. insufficient balance). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentFailedEvent {
    private UUID eventId;
    private UUID orderId;
    private String userId;
    private String reason;
    private Instant occurredAt;

    public static OrderPaymentFailedEvent of(UUID orderId, String userId, String reason) {
        return new OrderPaymentFailedEvent(
                UUID.randomUUID(), orderId, userId, reason, Instant.now());
    }
}
