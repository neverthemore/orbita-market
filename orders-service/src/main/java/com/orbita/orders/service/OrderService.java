package com.orbita.orders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbita.orders.domain.*;
import com.orbita.orders.dto.request.CreateOrderRequest;
import com.orbita.orders.event.OrderPaymentCompletedEvent;
import com.orbita.orders.event.OrderPaymentFailedEvent;
import com.orbita.orders.event.OrderPaymentRequestedEvent;
import com.orbita.orders.exception.*;
import com.orbita.orders.repository.OrderRepository;
import com.orbita.orders.repository.OutboxRepository;
import com.orbita.orders.repository.PaymentResultInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentResultInboxRepository resultInboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${orbita.kafka.topics.payment-requested}")
    private String paymentRequestedTopic;

    // ─── Required payload fields per product type ─────────────────────────

    private static final Map<String, List<String>> REQUIRED_FIELDS = Map.of(
            "ARCHIVE",    List.of("aoi", "capture_date", "sensor_type"),
            "TASKING",    List.of("aoi", "time_window", "sensor_type"),
            "MONITORING", List.of("aoi", "cadence", "duration_days")
    );

    /**
     * Create a new order and atomically enqueue an OrderPaymentRequested event
     * in the Outbox table.  The HTTP response returns immediately with
     * status PAYMENT_PENDING — the client must poll for the final status.
     *
     * Transactional Outbox: both the orders row and the outbox_events row
     * are written in the same DB transaction → either both commit or neither does.
     */
    @Transactional
    @SneakyThrows
    public Order createOrder(String userId, CreateOrderRequest req) {
        // ── Validate product type ─────────────────────────────────────────
        ProductType productType;
        try {
            productType = ProductType.valueOf(req.product_type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnknownProductTypeException(req.product_type());
        }

        // ── Validate price ────────────────────────────────────────────────
        if (req.price() == null || req.price() <= 0) {
            throw new InvalidPriceException();
        }

        // ── Validate payload fields ───────────────────────────────────────
        List<String> required = REQUIRED_FIELDS.get(productType.name());
        if (required != null) {
            for (String field : required) {
                if (!req.payload().containsKey(field)) {
                    throw new InvalidPayloadException("Missing required field in payload: " + field);
                }
            }
        }

        // ── Persist order ─────────────────────────────────────────────────
        Order order = new Order();
        order.setUserId(userId);
        order.setProductType(productType);
        order.setPrice(req.price());
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setPayload(objectMapper.writeValueAsString(req.payload()));
        orderRepository.save(order);

        // ── Write Outbox event (same transaction) ─────────────────────────
        OrderPaymentRequestedEvent event = OrderPaymentRequestedEvent.of(order.getId(), userId, req.price());
        String eventJson = objectMapper.writeValueAsString(event);
        OutboxEvent outboxEvent = new OutboxEvent(
                event.getEventId(), order.getId(), "OrderPaymentRequested", eventJson, paymentRequestedTopic);
        outboxRepository.save(outboxEvent);

        log.info("Order {} created, outbox event {} enqueued", order.getId(), event.getEventId());
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> listOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Order getOrder(String orderId, String userId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new OrderNotFoundException(orderId);
        }
        return orderRepository.findByIdAndUserId(uuid, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Apply a COMPLETED result from Payments Service.
     * Inbox guard ensures the same event is not applied twice.
     */
    @Transactional
    public void applyPaymentCompleted(OrderPaymentCompletedEvent event) {
        if (!insertResultInbox(event.getEventId(), event.getOrderId(), "COMPLETED")) {
            return; // duplicate
        }
        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            log.info("Order {} → PAID", order.getId());
        }, () -> log.warn("PaymentCompleted received for unknown order {}", event.getOrderId()));
    }

    /**
     * Apply a FAILED result from Payments Service.
     */
    @Transactional
    public void applyPaymentFailed(OrderPaymentFailedEvent event) {
        if (!insertResultInbox(event.getEventId(), event.getOrderId(), "FAILED")) {
            return; // duplicate
        }
        orderRepository.findById(event.getOrderId()).ifPresentOrElse(order -> {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailureReason(event.getReason());
            orderRepository.save(order);
            log.info("Order {} → PAYMENT_FAILED reason={}", order.getId(), event.getReason());
        }, () -> log.warn("PaymentFailed received for unknown order {}", event.getOrderId()));
    }

    /** @return true if inserted (first time), false if duplicate. */
    private boolean insertResultInbox(UUID eventId, UUID orderId, String type) {
        try {
            resultInboxRepository.saveAndFlush(new PaymentResultInbox(eventId, orderId, type));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate payment result event {} — skipping", eventId);
            return false;
        }
    }
}
