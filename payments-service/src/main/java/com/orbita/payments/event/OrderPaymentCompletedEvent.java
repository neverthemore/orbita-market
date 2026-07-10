package com.orbita.payments.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Published to Orders Service after successful debit. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentCompletedEvent {
    private UUID eventId;
    private UUID orderId;
    private String userId;
    private Long amount;
    private Long newBalance;
    private Instant occurredAt;

    public static OrderPaymentCompletedEvent of(UUID orderId, String userId, Long amount, Long newBalance) {
        return new OrderPaymentCompletedEvent(
                UUID.randomUUID(), orderId, userId, amount, newBalance, Instant.now());
    }
}
