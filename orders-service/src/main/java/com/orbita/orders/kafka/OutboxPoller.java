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
            }
        }
    }
}
