package com.orbita.orders.repository;

import com.orbita.orders.domain.PaymentResultInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentResultInboxRepository extends JpaRepository<PaymentResultInbox, UUID> {
    boolean existsByEventId(UUID eventId);
}
