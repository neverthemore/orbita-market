package com.orbita.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbita.orders.domain.*;
import com.orbita.orders.dto.request.CreateOrderRequest;
import com.orbita.orders.event.OrderPaymentCompletedEvent;
import com.orbita.orders.event.OrderPaymentFailedEvent;
import com.orbita.orders.exception.*;
import com.orbita.orders.repository.OrderRepository;
import com.orbita.orders.repository.OutboxRepository;
import com.orbita.orders.repository.PaymentResultInboxRepository;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Feature("Order Service")
class OrderServiceTest {

    @Mock OrderRepository              orderRepository;
    @Mock OutboxRepository             outboxRepository;
    @Mock PaymentResultInboxRepository resultInboxRepository;
    @Spy  ObjectMapper                 objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks OrderService orderService;

    @BeforeEach
    void setUp() {
        // paymentRequestedTopic is @Value-injected — no setter exists, so
        // ReflectionTestUtils is the correct tool here (unlike Order fields below).
        ReflectionTestUtils.setField(orderService, "paymentRequestedTopic", "order-payment-requested");
    }

    // ─── createOrder — happy paths per product type ───────────────────────

    @Test
    @DisplayName("createOrder ARCHIVE: valid request → PAYMENT_PENDING + outbox event saved")
    @Story("Order creation")
    @Description("Scenario 1: Happy path")
    void createOrder_archive_success() {
        CreateOrderRequest req = new CreateOrderRequest(
                "ARCHIVE", 120L,
                Map.of("aoi", "POLYGON(...)", "capture_date", "2024-06-15", "sensor_type", "MSI")
        );

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder("user-42", req);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getProductType()).isEqualTo(ProductType.ARCHIVE);
        assertThat(order.getPrice()).isEqualTo(120L);
        assertThat(order.getUserId()).isEqualTo("user-42");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("OrderPaymentRequested");
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo("order-payment-requested");
        assertThat(outboxCaptor.getValue().getSent()).isFalse();
        assertThat(outboxCaptor.getValue().getPayload()).contains("\"amount\":120");
    }

    @Test
    @DisplayName("createOrder TASKING: valid request → PAYMENT_PENDING")
    @Story("Order creation")
    void createOrder_tasking_success() {
        CreateOrderRequest req = new CreateOrderRequest(
                "TASKING", 500L,
                Map.of("aoi", "POLYGON(...)", "time_window", "2024-07-01/2024-07-15", "sensor_type", "SAR")
        );

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder("user-42", req);

        assertThat(order.getProductType()).isEqualTo(ProductType.TASKING);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("createOrder MONITORING: valid request → PAYMENT_PENDING")
    @Story("Order creation")
    void createOrder_monitoring_success() {
        CreateOrderRequest req = new CreateOrderRequest(
                "MONITORING", 2000L,
                Map.of("aoi", "POLYGON(...)", "cadence", "WEEKLY", "duration_days", 90)
        );

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder("user-42", req);

        assertThat(order.getProductType()).isEqualTo(ProductType.MONITORING);
    }

    @Test
    @DisplayName("createOrder: case-insensitive product_type ('archive' → ARCHIVE)")
    @Story("Order creation")
    void createOrder_lowercaseProductType_normalized() {
        CreateOrderRequest req = new CreateOrderRequest(
                "archive", 120L,
                Map.of("aoi", "x", "capture_date", "2024-06-15", "sensor_type", "MSI")
        );
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrder("user-42", req);

        assertThat(order.getProductType()).isEqualTo(ProductType.ARCHIVE);
    }

    // ─── createOrder — validation ──────────────────────────────────────────

    @Test
    @DisplayName("createOrder: unknown product type → UnknownProductTypeException")
    @Story("Validation")
    void createOrder_unknownProductType_throws() {
        CreateOrderRequest req = new CreateOrderRequest("INVALID", 100L, Map.of("aoi", "x"));
        assertThatThrownBy(() -> orderService.createOrder("user-42", req))
                .isInstanceOf(UnknownProductTypeException.class)
                .hasMessageContaining("INVALID");

        verifyNoInteractions(orderRepository, outboxRepository);
    }

    @ParameterizedTest(name = "createOrder ARCHIVE missing field: {0}")
    @ValueSource(strings = {"aoi", "capture_date", "sensor_type"})
    @DisplayName("createOrder ARCHIVE: each required field is enforced individually")
    @Story("Validation")
    @Description("ARCHIVE requires aoi, capture_date, sensor_type — each checked independently")
    void createOrder_archive_missingEachRequiredField(String missingField) {
        Map<String, Object> fullPayload = new HashMap<>(Map.of(
                "aoi", "POLYGON(...)", "capture_date", "2024-06-15", "sensor_type", "MSI"));
        fullPayload.remove(missingField);

        CreateOrderRequest req = new CreateOrderRequest("ARCHIVE", 100L, fullPayload);

        assertThatThrownBy(() -> orderService.createOrder("user-42", req))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining(missingField);
    }

    @Test
    @DisplayName("createOrder MONITORING: missing cadence → InvalidPayloadException")
    @Story("Validation")
    void createOrder_monitoring_missingCadence_throws() {
        CreateOrderRequest req = new CreateOrderRequest(
                "MONITORING", 2000L, Map.of("aoi", "x", "duration_days", 30));
        assertThatThrownBy(() -> orderService.createOrder("user-42", req))
                .isInstanceOf(InvalidPayloadException.class)
                .hasMessageContaining("cadence");
    }

    @Test
    @DisplayName("createOrder: zero price → InvalidPriceException (service-level safety net)")
    @Story("Validation")
    @Description("Defense in depth: even if a caller bypasses @Valid, the service rejects price<=0")
    void createOrder_zeroPrice_throws() {
        CreateOrderRequest req = new CreateOrderRequest(
                "ARCHIVE", 0L, Map.of("aoi", "x", "capture_date", "2024-06-15", "sensor_type", "MSI"));
        assertThatThrownBy(() -> orderService.createOrder("user-42", req))
                .isInstanceOf(InvalidPriceException.class);
    }

    // ─── listOrders / getOrder ──────────────────────────────────────────────

    @Test
    @DisplayName("listOrders: returns orders sorted by repository (delegate, no transform)")
    @Story("Order listing")
    void listOrders_delegatesToRepository() {
        Order o1 = new Order();
        o1.setUserId("user-42");
        when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-42")).thenReturn(List.of(o1));

        List<Order> result = orderService.listOrders("user-42");

        assertThat(result).hasSize(1).containsExactly(o1);
    }

    @Test
    @DisplayName("getOrder: valid UUID + owner → returns order")
    @Story("Order details")
    void getOrder_validOwner_returnsOrder() {
        UUID id = UUID.randomUUID();
        Order order = new Order();
        order.setId(id);
        order.setUserId("user-42");
        when(orderRepository.findByIdAndUserId(id, "user-42")).thenReturn(Optional.of(order));

        Order result = orderService.getOrder(id.toString(), "user-42");

        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("getOrder: malformed UUID string → OrderNotFoundException (not 500)")
    @Story("Order details")
    @Description("Defensive parsing: invalid UUID format must map to 404, not crash")
    void getOrder_malformedUuid_throwsNotFound() {
        assertThatThrownBy(() -> orderService.getOrder("not-a-uuid", "user-42"))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("getOrder: order belongs to different user → OrderNotFoundException")
    @Story("Order details")
    @Description("Owner isolation: findByIdAndUserId returns empty for non-owners")
    void getOrder_wrongOwner_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdAndUserId(id, "user-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id.toString(), "user-99"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ─── applyPaymentCompleted ────────────────────────────────────────────

    @Test
    @DisplayName("applyPaymentCompleted: order moved to PAID")
    @Story("Payment result")
    @Description("Scenario 1: After successful debit order status → PAID")
    void applyPaymentCompleted_success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        OrderPaymentCompletedEvent event = new OrderPaymentCompletedEvent(
                UUID.randomUUID(), orderId, "user-42", 120L, 880L);

        when(resultInboxRepository.saveAndFlush(any())).thenReturn(new PaymentResultInbox());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.applyPaymentCompleted(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("applyPaymentCompleted: duplicate event → skipped (inbox idempotency)")
    @Story("Idempotency")
    @Description("Scenario 3: Duplicate PaymentCompleted must not change status twice")
    void applyPaymentCompleted_duplicateEvent_skipped() {
        OrderPaymentCompletedEvent event = new OrderPaymentCompletedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "user-42", 120L, 880L);

        when(resultInboxRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        orderService.applyPaymentCompleted(event);

        verify(orderRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyPaymentCompleted: order not found in DB → no exception, just logged")
    @Story("Payment result")
    @Description("Defensive: must not throw if order row is somehow missing")
    void applyPaymentCompleted_orderNotFound_doesNotThrow() {
        UUID orderId = UUID.randomUUID();
        OrderPaymentCompletedEvent event = new OrderPaymentCompletedEvent(
                UUID.randomUUID(), orderId, "user-42", 120L, 880L);

        when(resultInboxRepository.saveAndFlush(any())).thenReturn(new PaymentResultInbox());
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatCode(() -> orderService.applyPaymentCompleted(event)).doesNotThrowAnyException();
        verify(orderRepository, never()).save(any());
    }

    // ─── applyPaymentFailed ───────────────────────────────────────────────

    @Test
    @DisplayName("applyPaymentFailed: order moved to PAYMENT_FAILED with reason")
    @Story("Payment result")
    @Description("Scenario 2: Insufficient balance → PAYMENT_FAILED")
    void applyPaymentFailed_success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PAYMENT_PENDING);

        OrderPaymentFailedEvent event = new OrderPaymentFailedEvent(
                UUID.randomUUID(), orderId, "user-42", "INSUFFICIENT_BALANCE");

        when(resultInboxRepository.saveAndFlush(any())).thenReturn(new PaymentResultInbox());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.applyPaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getFailureReason()).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("applyPaymentFailed: duplicate event → skipped")
    @Story("Idempotency")
    void applyPaymentFailed_duplicateEvent_skipped() {
        OrderPaymentFailedEvent event = new OrderPaymentFailedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "user-42", "INSUFFICIENT_BALANCE");

        when(resultInboxRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate"));

        orderService.applyPaymentFailed(event);

        verify(orderRepository, never()).findById(any());
    }
}
