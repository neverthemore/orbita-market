package com.orbita.orders.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Received from Payments Service: debit succeeded. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPaymentCompletedEvent {
    private UUID eventId;
    private UUID orderId;
    private String userId;
    private Long amount;
    private Long newBalance;
}
