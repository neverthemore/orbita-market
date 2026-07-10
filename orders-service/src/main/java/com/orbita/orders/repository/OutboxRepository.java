package com.orbita.orders.repository;

import com.orbita.orders.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Native query with FOR UPDATE SKIP LOCKED:
     *   — FOR UPDATE:      row-level lock, prevents concurrent reads by other transactions
     *   — SKIP LOCKED:     skips rows already locked → safe for multi-instance deployments;
     *                      each instance picks its own batch without blocking others.
     *
     * Note: @Lock(PESSIMISTIC_WRITE) is NOT used with nativeQuery = true because
     * Spring Data JPA appends the hint at the JPQL level; for native SQL we embed
     * the locking clause directly.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE sent = false
            ORDER BY created_at ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findUnsentEventsForUpdate();
}
