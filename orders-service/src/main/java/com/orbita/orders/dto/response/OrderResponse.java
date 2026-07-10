package com.orbita.orders.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbita.orders.domain.Order;
import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
        UUID order_id,
        String status,
        String product_type,
        Long price,
        String user_id,
        String failure_reason,
        Map<String, Object> payload,
        LocalDateTime created_at,
        LocalDateTime updated_at
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @SneakyThrows
    public static OrderResponse of(Order order) {
        Map<String, Object> payload = null;
        if (order.getPayload() != null) {
            payload = MAPPER.readValue(order.getPayload(), MAP_TYPE);
        }
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getProductType().name(),
                order.getPrice(),
                order.getUserId(),
                order.getFailureReason(),
                payload,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
