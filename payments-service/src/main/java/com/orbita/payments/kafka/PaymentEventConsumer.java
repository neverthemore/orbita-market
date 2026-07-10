package com.orbita.payments.kafka;

import com.orbita.payments.event.OrderPaymentCompletedEvent;
import com.orbita.payments.event.OrderPaymentFailedEvent;
import com.orbita.payments.event.OrderPaymentRequestedEvent;
import com.orbita.payments.service.AccountService;
import com.orbita.payments.service.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for OrderPaymentRequested events.
 *
 * Orchestration order (critical for correctness):
 *  1. accountService.processPaymentRequest() — all DB work, runs in @Transactional.
 *     Returns a PaymentResult WITHOUT publishing to Kafka.
 *  2. Transaction commits (method returns).
 *  3. eventProducer.publish*() — Kafka publish happens AFTER commit.
 *     This ensures Orders cannot receive OrderPaymentCompleted before the
 *     debit is visible in payments_db.
 *  4. ack.acknowledge() — commit Kafka offset only after full success.
 *
 * If step 3 or 4 fails the service will restart from step 1 on redelivery;
 * the inbox idempotency guard in AccountService prevents double-debit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final AccountService       accountService;
    private final PaymentEventProducer eventProducer;

    @KafkaListener(
            topics = "${orbita.kafka.topics.payment-requested}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPaymentRequested(OrderPaymentRequestedEvent event, Acknowledgment ack) {
        log.info("Received OrderPaymentRequested: eventId={} orderId={}",
                event.getEventId(), event.getOrderId());
        try {
            // Step 1 + 2: DB work + commit
            PaymentResult result = accountService.processPaymentRequest(event);

            // Step 3: Kafka publish — outside @Transactional boundary
            if (result.isDuplicate()) {
                log.info("Duplicate event {} — no Kafka publish needed", event.getEventId());
            } else if (result.isSuccess()) {
                eventProducer.publishPaymentCompleted(
                        OrderPaymentCompletedEvent.of(
                                result.orderId(), result.userId(),
                                result.amount(), result.newBalance()));
            } else {
                eventProducer.publishPaymentFailed(
                        OrderPaymentFailedEvent.of(
                                result.orderId(), result.userId(), result.reason()));
            }

            // Step 4: Acknowledge Kafka offset
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process OrderPaymentRequested orderId={}: {}",
                    event.getOrderId(), ex.getMessage(), ex);
            // Do NOT ack — Kafka will redeliver; inbox prevents double-processing.
            throw ex;
        }
    }
}
