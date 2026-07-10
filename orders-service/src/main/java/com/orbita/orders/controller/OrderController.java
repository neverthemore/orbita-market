package com.orbita.orders.controller;

import com.orbita.orders.domain.Order;
import com.orbita.orders.dto.request.CreateOrderRequest;
import com.orbita.orders.dto.response.OrderResponse;
import com.orbita.orders.exception.MissingUserIdException;
import com.orbita.orders.service.OrderService;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Feature("Orders API")
public class OrderController {

    private final OrderService orderService;


    @PostMapping("/orders")
    @Description("Create a satellite product order")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        validateUserId(userId);
        Order order = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.of(order));
    }

    @GetMapping("/orders")
    @Description("List all orders for the authenticated user")
    public ResponseEntity<List<OrderResponse>> listOrders(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        validateUserId(userId);
        List<OrderResponse> orders = orderService.listOrders(userId).stream()
                .map(OrderResponse::of)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/{orderId}")
    @Description("Get order details and payment status")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String orderId) {
        validateUserId(userId);
        Order order = orderService.getOrder(orderId, userId);
        return ResponseEntity.ok(OrderResponse.of(order));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new MissingUserIdException();
        }
    }
}
