package com.orbita.orders.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Inbox for payment result events coming from Payments Service.
 * Guards against duplicate OrderPaymentCompleted / OrderPaymentFailed deliveries:
 * if the same event_id arrives twice, the UNIQUE constraint rejects the insert
 * and the listener skips processing.
 */
@Entity
@Table(name = "payment_result_inbox")
@Getter
@Setter
@NoArgsConstructor
public class PaymentResultInbox {

    @Id
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public PaymentResultInbox(UUID eventId, UUID orderId, String eventType) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
    }
}
