package com.orbita.orders.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox record.
 *
 * Written in the same DB transaction as the Order — so either both persist
 * or neither does.  A background poller (OutboxPoller) reads unsent rows
 * and publishes them to Kafka, then marks them as sent.
 *
 * This decouples order persistence from Kafka availability and guarantees
 * at-least-once delivery of OrderPaymentRequested events.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The event_id embedded in the payload — used as idempotency key by Payments inbox. */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    /** Logical aggregate this event belongs to (order_id). */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** E.g. "OrderPaymentRequested". */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** JSON payload to be published verbatim to Kafka. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    /** Topic to publish to. */
    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private Boolean sent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public OutboxEvent(UUID eventId, UUID aggregateId, String eventType, String payload, String topic) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.topic = topic;
        this.sent = false;
    }
}
