package com.orbita.orders.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Received from Payments Service: debit failed. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPaymentFailedEvent {
    private UUID eventId;
    private UUID orderId;
    private String userId;
    private String reason;
}
