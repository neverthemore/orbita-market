package com.orbita.payments.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Received from Orders Service — request to debit user's balance. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPaymentRequestedEvent {
    private UUID eventId;
    private UUID orderId;
    private String userId;
    private Long amount;
    private Instant occurredAt;
}
