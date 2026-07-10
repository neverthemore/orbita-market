package com.orbita.payments.repository;

import com.orbita.payments.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(String userId);

    boolean existsByUserId(String userId);

    /**
     * SELECT ... FOR UPDATE — used during payment processing to prevent
     * concurrent threads from reading a stale balance.
     * Combined with @Version this gives us two layers of concurrency protection.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    Optional<Account> findByUserIdWithLock(@Param("userId") String userId);
}
