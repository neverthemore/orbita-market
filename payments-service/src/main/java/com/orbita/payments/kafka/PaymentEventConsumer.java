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


            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process OrderPaymentRequested orderId={}: {}",
                    event.getOrderId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
