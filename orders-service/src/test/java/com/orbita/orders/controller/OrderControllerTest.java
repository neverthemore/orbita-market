package com.orbita.orders.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbita.orders.domain.Order;
import com.orbita.orders.domain.OrderStatus;
import com.orbita.orders.domain.ProductType;
import com.orbita.orders.exception.OrderNotFoundException;
import com.orbita.orders.service.OrderService;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Feature("Orders API")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "orbita.kafka.topics.payment-requested=order-payment-requested",
    "orbita.kafka.topics.payment-completed=order-payment-completed",
    "orbita.kafka.topics.payment-failed=order-payment-failed",
    "orbita.outbox.poll-interval-ms=60000"
})
class OrderControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  OrderService orderService;

    private static final UUID TEST_ORDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setId(TEST_ORDER_ID);
        testOrder.setUserId("user-42");
        testOrder.setProductType(ProductType.ARCHIVE);
        testOrder.setPrice(120L);
        testOrder.setStatus(OrderStatus.PAYMENT_PENDING);
        testOrder.setPayload("{\"aoi\":\"POLYGON((0 0,1 0,1 1,0 1,0 0))\",\"capture_date\":\"2024-06-15\",\"sensor_type\":\"MSI\"}");
    }

    // ─── POST /orders ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /orders → 201 PAYMENT_PENDING")
    @Story("Order creation")
    @Description("Scenario 1: Happy path — create ARCHIVE order")
    void createOrder_success() throws Exception {
        when(orderService.createOrder(eq("user-42"), any())).thenReturn(testOrder);

        Map<String, Object> body = Map.of(
                "product_type", "ARCHIVE",
                "price", 120,
                "payload", Map.of("aoi", "POLYGON((0 0,1 0,1 1,0 1,0 0))",
                        "capture_date", "2024-06-15",
                        "sensor_type", "MSI")
        );

        mockMvc.perform(post("/api/v1/orders/orders")
                        .header("X-User-Id", "user-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order_id").value(TEST_ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.product_type").value("ARCHIVE"))
                .andExpect(jsonPath("$.price").value(120));
    }

    @Test
    @DisplayName("POST /orders without X-User-Id → 400 MISSING_USER_ID")
    @Story("Validation")
    void createOrder_missingUserId_returns400() throws Exception {
        Map<String, Object> body = Map.of("product_type", "ARCHIVE", "price", 120,
                "payload", Map.of("aoi", "x", "capture_date", "2024-06-15", "sensor_type", "MSI"));

        mockMvc.perform(post("/api/v1/orders/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("MISSING_USER_ID"));
    }

    @Test
    @DisplayName("POST /orders with price=0 → 400 (bean validation @Positive)")
    @Story("Validation")
    void createOrder_invalidPrice_returns400() throws Exception {
        Map<String, Object> body = Map.of("product_type", "ARCHIVE", "price", 0,
                "payload", Map.of("aoi", "x", "capture_date", "2024-06-15", "sensor_type", "MSI"));

        mockMvc.perform(post("/api/v1/orders/orders")
                        .header("X-User-Id", "user-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /orders with blank product_type → 400 (bean validation @NotBlank)")
    @Story("Validation")
    void createOrder_blankProductType_returns400() throws Exception {
        Map<String, Object> body = Map.of("product_type", "", "price", 100,
                "payload", Map.of("aoi", "x"));

        mockMvc.perform(post("/api/v1/orders/orders")
                        .header("X-User-Id", "user-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /orders ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /orders → 200 with list of orders")
    @Story("Order listing")
    void listOrders_success() throws Exception {
        when(orderService.listOrders("user-42")).thenReturn(List.of(testOrder));

        mockMvc.perform(get("/api/v1/orders/orders")
                        .header("X-User-Id", "user-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].order_id").value(TEST_ORDER_ID.toString()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /orders with no orders → 200 empty list")
    @Story("Order listing")
    void listOrders_empty_returnsEmptyList() throws Exception {
        when(orderService.listOrders("user-new")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/orders")
                        .header("X-User-Id", "user-new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── GET /orders/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /orders/{id} → 200 with order details")
    @Story("Order details")
    @Description("Happy path: retrieve order by ID")
    void getOrder_success() throws Exception {
        when(orderService.getOrder(TEST_ORDER_ID.toString(), "user-42")).thenReturn(testOrder);

        mockMvc.perform(get("/api/v1/orders/orders/" + TEST_ORDER_ID)
                        .header("X-User-Id", "user-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.payload.aoi").value("POLYGON((0 0,1 0,1 1,0 1,0 0))"));
    }

    @Test
    @DisplayName("GET /orders/{id} for another user's order → 404 ORDER_NOT_FOUND")
    @Story("Order details")
    @Description("Owner isolation: user cannot see another user's order")
    void getOrder_wrongUser_returns404() throws Exception {
        when(orderService.getOrder(anyString(), eq("user-99")))
                .thenThrow(new OrderNotFoundException(TEST_ORDER_ID.toString()));

        mockMvc.perform(get("/api/v1/orders/orders/" + TEST_ORDER_ID)
                        .header("X-User-Id", "user-99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("ORDER_NOT_FOUND"));
    }
}
