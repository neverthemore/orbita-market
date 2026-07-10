package com.orbita.orders.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbita.orders.event.OrderPaymentCompletedEvent;
import com.orbita.orders.event.OrderPaymentFailedEvent;
import com.orbita.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${orbita.kafka.topics.payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(String payload, Acknowledgment ack) {
        try {
            OrderPaymentCompletedEvent event =
                    objectMapper.readValue(payload, OrderPaymentCompletedEvent.class);
            log.info("Received PaymentCompleted: orderId={}", event.getOrderId());
            orderService.applyPaymentCompleted(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error handling PaymentCompleted payload={}: {}", payload, ex.getMessage(), ex);
            throw new RuntimeException("Failed to process PaymentCompleted", ex);
        }
    }

    @KafkaListener(
            topics = "${orbita.kafka.topics.payment-failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(String payload, Acknowledgment ack) {
        try {
            OrderPaymentFailedEvent event =
                    objectMapper.readValue(payload, OrderPaymentFailedEvent.class);
            log.info("Received PaymentFailed: orderId={} reason={}", event.getOrderId(), event.getReason());
            orderService.applyPaymentFailed(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Error handling PaymentFailed payload={}: {}", payload, ex.getMessage(), ex);
            throw new RuntimeException("Failed to process PaymentFailed", ex);
        }
    }
}
