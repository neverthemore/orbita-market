package com.orbita.payments.kafka;

import com.orbita.payments.event.OrderPaymentCompletedEvent;
import com.orbita.payments.event.OrderPaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${orbita.kafka.topics.payment-completed}")
    private String paymentCompletedTopic;

    @Value("${orbita.kafka.topics.payment-failed}")
    private String paymentFailedTopic;

    public void publishPaymentCompleted(OrderPaymentCompletedEvent event) {
        log.info("Publishing PaymentCompleted for order={}", event.getOrderId());
        kafkaTemplate.send(paymentCompletedTopic, event.getOrderId().toString(), event);
    }

    public void publishPaymentFailed(OrderPaymentFailedEvent event) {
        log.info("Publishing PaymentFailed for order={} reason={}", event.getOrderId(), event.getReason());
        kafkaTemplate.send(paymentFailedTopic, event.getOrderId().toString(), event);
    }
}
