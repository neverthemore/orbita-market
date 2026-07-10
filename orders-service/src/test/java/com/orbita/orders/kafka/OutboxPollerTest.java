package com.orbita.orders.kafka;

import com.orbita.orders.domain.OutboxEvent;
import com.orbita.orders.repository.OutboxRepository;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Feature("Outbox Poller")
class OutboxPollerTest {

    @Mock OutboxRepository              outboxRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks OutboxPoller outboxPoller;

    private OutboxEvent buildEvent() {
        return new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "OrderPaymentRequested",
                "{\"eventId\":\"abc\",\"orderId\":\"def\"}",
                "order-payment-requested"
        );
    }

    @Test
    @DisplayName("poll: publishes event and marks as sent after Kafka ACK")
    @Story("Transactional Outbox — at-least-once delivery")
    void poll_publishesAndMarksAsSent() {
        OutboxEvent event = buildEvent();

        when(outboxRepository.findUnsentEventsForUpdate()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxPoller.poll();

        verify(kafkaTemplate).send(
                eq("order-payment-requested"),
                eq(event.getAggregateId().toString()),
                eq(event.getPayload())
        );

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(saved.capture());
        assertThat(saved.getValue().getSent()).isTrue();
        assertThat(saved.getValue().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("poll: no pending events → nothing published")
    @Story("Transactional Outbox — idle cycle")
    void poll_noPendingEvents_nothingPublished() {
        when(outboxRepository.findUnsentEventsForUpdate()).thenReturn(List.of());

        outboxPoller.poll();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("poll: Kafka timeout → event stays unsent, retried next cycle")
    @Story("Transactional Outbox — retry on failure")
    void poll_kafkaFails_eventStaysUnsent() {
        OutboxEvent event = buildEvent();

        when(outboxRepository.findUnsentEventsForUpdate()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

        // Should NOT throw — errors are caught, event left unsent for retry
        outboxPoller.poll();

        assertThat(event.getSent()).isFalse();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("poll: first event fails, second succeeds → second is sent")
    @Story("Transactional Outbox — partial batch")
    void poll_partialBatch_secondEventSent() {
        OutboxEvent failEvent    = buildEvent();
        OutboxEvent successEvent = buildEvent();

        when(outboxRepository.findUnsentEventsForUpdate())
                .thenReturn(List.of(failEvent, successEvent));
        when(kafkaTemplate.send(anyString(), eq(failEvent.getAggregateId().toString()), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));
        when(kafkaTemplate.send(anyString(), eq(successEvent.getAggregateId().toString()), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxPoller.poll();

        assertThat(failEvent.getSent()).isFalse();
        assertThat(successEvent.getSent()).isTrue();
        verify(outboxRepository, times(1)).save(any()); // only successEvent saved
    }
}
