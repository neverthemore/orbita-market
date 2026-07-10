package com.orbita.orders.repository;

import com.orbita.orders.domain.Order;
import com.orbita.orders.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Order> findByIdAndUserId(UUID id, String userId);

    List<Order> findByStatus(OrderStatus status);
}
