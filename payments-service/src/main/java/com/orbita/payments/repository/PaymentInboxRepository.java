package com.orbita.payments.repository;

import com.orbita.payments.domain.PaymentInboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentInboxRepository extends JpaRepository<PaymentInboxEntry, UUID> {

    boolean existsByEventId(UUID eventId);
}
