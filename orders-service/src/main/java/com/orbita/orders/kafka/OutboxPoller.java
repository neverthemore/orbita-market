package com.orbita.orders.kafka;

import com.orbita.orders.domain.OutboxEvent;
import com.orbita.orders.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transactional Outbox Poller.
 *
 * Reads unsent events from outbox_events (with FOR UPDATE SKIP LOCKED) and
 * publishes them to Kafka synchronously using send().get(timeout).
 *
 * Why synchronous send?
 *   - We only mark an event as sent AFTER Kafka confirms receipt.
 *   - If Kafka is down, the event stays unsent and is retried next cycle.
 *   - Combined with the Payments inbox idempotency guard, this gives us
 *     at-least-once delivery without duplicates reaching the business logic.
 *
 * SKIP LOCKED makes this safe to run on multiple instances simultaneously:
 * each node processes a disjoint batch of events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxRepository           outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    @Scheduled(fixedDelayString = "${orbita.outbox.poll-interval-ms:1000}")
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = outboxRepository.findUnsentEventsForUpdate();
        if (pending.isEmpty()) return;

        log.debug("OutboxPoller: {} event(s) to publish", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Synchronous send — blocks until Kafka broker confirms.
                // Only mark as sent after broker ACK to guarantee at-least-once delivery.
                kafkaTemplate
                        .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.setSent(true);
                event.setSentAt(LocalDateTime.now());
                outboxRepository.save(event);

                log.info("Outbox: published eventId={} type={} to {}",
                        event.getEventId(), event.getEventType(), event.getTopic());

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Outbox poller interrupted", ie);
                break;
            } catch (Exception ex) {
                log.error("Outbox: failed to publish eventId={} — will retry next cycle: {}",
                        event.getEventId(), ex.getMessage());
                // Leave sent=false — next poll will retry.
                // SKIP LOCKED ensures another instance won't grab this event
                // while it's still in the same transaction.
            }
        }
    }
}
