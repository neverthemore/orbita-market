package com.orbita.payments.kafka;

import com.orbita.payments.event.OrderPaymentCompletedEvent;
import com.orbita.payments.event.OrderPaymentFailedEvent;
import com.orbita.payments.event.OrderPaymentRequestedEvent;
import com.orbita.payments.service.AccountService;
import com.orbita.payments.service.PaymentResult;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Feature("Payment Event Consumer")
class PaymentEventConsumerTest {

    @Mock AccountService        accountService;
    @Mock PaymentEventProducer  eventProducer;
    @Mock Acknowledgment        ack;

    @InjectMocks PaymentEventConsumer consumer;

    private OrderPaymentRequestedEvent buildEvent(UUID orderId, UUID eventId) {
        return new OrderPaymentRequestedEvent(eventId, orderId, "user-42", 120L, Instant.now());
    }

    // ─── Happy path ───────────────────────────────────────────────────────

    @Test
    @DisplayName("SUCCESS result → publishes PaymentCompleted and ACKs")
    @Story("Happy path")
    @Description("Kafka publish happens AFTER transaction commit (correct ordering)")
    void success_publishesCompletedAndAcks() {
        UUID orderId  = UUID.randomUUID();
        UUID eventId  = UUID.randomUUID();
        OrderPaymentRequestedEvent event = buildEvent(orderId, eventId);

        when(accountService.processPaymentRequest(event))
                .thenReturn(PaymentResult.success(orderId, "user-42", 120L, 880L));

        consumer.handleOrderPaymentRequested(event, ack);

        ArgumentCaptor<OrderPaymentCompletedEvent> captor =
                ArgumentCaptor.forClass(OrderPaymentCompletedEvent.class);
        verify(eventProducer).publishPaymentCompleted(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getNewBalance()).isEqualTo(880L);

        verify(eventProducer, never()).publishPaymentFailed(any());
        verify(ack).acknowledge();
    }

    // ─── Failure ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("FAILURE result → publishes PaymentFailed with correct reason and ACKs")
    @Story("Insufficient balance")
    @Description("Scenario 2: balance 50, order 120 → PAYMENT_FAILED")
    void failure_publishesFailedAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderPaymentRequestedEvent event = buildEvent(orderId, UUID.randomUUID());

        when(accountService.processPaymentRequest(event))
                .thenReturn(PaymentResult.failure(orderId, "user-42", "INSUFFICIENT_BALANCE"));

        consumer.handleOrderPaymentRequested(event, ack);

        ArgumentCaptor<OrderPaymentFailedEvent> captor =
                ArgumentCaptor.forClass(OrderPaymentFailedEvent.class);
        verify(eventProducer).publishPaymentFailed(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("INSUFFICIENT_BALANCE");

        verify(eventProducer, never()).publishPaymentCompleted(any());
        verify(ack).acknowledge();
    }

    // ─── Duplicate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("DUPLICATE result → no Kafka publish, still ACKs (safe to skip)")
    @Story("Idempotency")
    @Description("Scenario 3: repeat delivery of same event_id → no double charge, no double event")
    void duplicate_noPublishButAcks() {
        OrderPaymentRequestedEvent event = buildEvent(UUID.randomUUID(), UUID.randomUUID());

        when(accountService.processPaymentRequest(event))
                .thenReturn(PaymentResult.duplicate());

        consumer.handleOrderPaymentRequested(event, ack);

        verify(eventProducer, never()).publishPaymentCompleted(any());
        verify(eventProducer, never()).publishPaymentFailed(any());
        verify(ack).acknowledge();
    }

    // ─── Error handling ───────────────────────────────────────────────────

    @Test
    @DisplayName("Service throws → no ACK (Kafka will redeliver)")
    @Story("Error handling")
    void serviceThrows_doesNotAck() {
        OrderPaymentRequestedEvent event = buildEvent(UUID.randomUUID(), UUID.randomUUID());

        when(accountService.processPaymentRequest(any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        try {
            consumer.handleOrderPaymentRequested(event, ack);
        } catch (RuntimeException ignored) {}

        verify(ack, never()).acknowledge();
        verify(eventProducer, never()).publishPaymentCompleted(any());
        verify(eventProducer, never()).publishPaymentFailed(any());
    }
}
