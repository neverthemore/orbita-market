package com.orbita.payments.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "payment_inbox")
@Getter
@Setter
@NoArgsConstructor
public class PaymentInboxEntry {

    @Id
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public PaymentInboxEntry(UUID eventId, UUID orderId, String userId) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.userId = userId;
        this.status = "PROCESSING";
    }
}
