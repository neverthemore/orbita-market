package com.orbita.payments.service;

import com.orbita.payments.domain.Account;
import com.orbita.payments.domain.PaymentInboxEntry;
import com.orbita.payments.event.OrderPaymentRequestedEvent;
import com.orbita.payments.exception.AccountNotFoundException;
import com.orbita.payments.repository.AccountRepository;
import com.orbita.payments.repository.PaymentInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core account/billing logic.
 *
 * IMPORTANT: processPaymentRequest() performs ONLY DB work and returns a
 * PaymentResult. Kafka publishing is intentionally NOT done here — it must
 * happen AFTER the transaction commits (in PaymentEventConsumer) to prevent
 * Orders from receiving the completion event before the debit is visible in
 * payments_db.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository       accountRepository;
    private final PaymentInboxRepository  inboxRepository;

    // ─── HTTP endpoints ───────────────────────────────────────────────────

    /**
     * Idempotent create-or-get.
     *
     * Fast path: an existing account is found and returned as-is.
     *
     * Race handling: if two requests for the same new userId arrive
     * concurrently, both may pass the findByUserId() check and both attempt
     * save(). The accounts.user_id UNIQUE constraint lets exactly one INSERT
     * succeed; the other raises DataIntegrityViolationException, which we
     * catch and resolve by re-fetching the winner's row. This is what makes
     * the operation genuinely idempotent under concurrency — both callers
     * get a successful, consistent result instead of one of them failing.
     */
    @Transactional
    public AccountCreationResult createAccount(String userId) {
        return accountRepository.findByUserId(userId)
                .map(existing -> new AccountCreationResult(existing, false))
                .orElseGet(() -> createNewAccount(userId));
    }

    private AccountCreationResult createNewAccount(String userId) {
        try {
            Account saved = accountRepository.save(new Account(userId));
            return new AccountCreationResult(saved, true);
        } catch (DataIntegrityViolationException raceLost) {
            log.info("Lost account-creation race for userId={} — returning winner's account", userId);
            Account winner = accountRepository.findByUserId(userId)
                    .orElseThrow(() -> raceLost); // practically unreachable; preserves original error if it somehow is
            return new AccountCreationResult(winner, false);
        }
    }

    @Transactional
    public Account topUp(String userId, long amount) {
        Account account = accountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
        account.setBalance(account.getBalance() + amount);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getBalance(String userId) {
        return accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(userId));
    }

    // ─── Kafka consumer support ───────────────────────────────────────────

    /**
     * Idempotently debit the balance for an order.
     * Returns a {@link PaymentResult} describing what happened.
     * The caller (PaymentEventConsumer) is responsible for publishing the
     * corresponding Kafka event AFTER this method returns (i.e., after commit).
     *
     * Inbox pattern: the unique constraint on payment_inbox.event_id ensures
     * that even if the same Kafka message is delivered multiple times, the
     * debit is applied at most once.
     */
    @Transactional
    public PaymentResult processPaymentRequest(OrderPaymentRequestedEvent event) {
        log.info("Processing payment orderId={} userId={} amount={}",
                event.getOrderId(), event.getUserId(), event.getAmount());

        // ── Inbox: guard against duplicate event delivery ─────────────────
        PaymentInboxEntry inboxEntry = new PaymentInboxEntry(
                event.getEventId(), event.getOrderId(), event.getUserId());
        try {
            inboxRepository.saveAndFlush(inboxEntry);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate event {} — skipping", event.getEventId());
            return PaymentResult.duplicate();
        }

        // ── Resolve account with pessimistic lock ─────────────────────────
        Account account;
        try {
            account = accountRepository.findByUserIdWithLock(event.getUserId())
                    .orElseThrow(() -> new AccountNotFoundException(event.getUserId()));
        } catch (AccountNotFoundException ex) {
            markInbox(inboxEntry, "FAILED");
            return PaymentResult.failure(event.getOrderId(), event.getUserId(), "ACCOUNT_NOT_FOUND");
        }

        // ── Check balance ─────────────────────────────────────────────────
        if (account.getBalance() < event.getAmount()) {
            log.warn("Insufficient balance orderId={}: balance={} required={}",
                    event.getOrderId(), account.getBalance(), event.getAmount());
            markInbox(inboxEntry, "FAILED");
            return PaymentResult.failure(event.getOrderId(), event.getUserId(), "INSUFFICIENT_BALANCE");
        }

        // ── Debit ─────────────────────────────────────────────────────────
        account.setBalance(account.getBalance() - event.getAmount());
        accountRepository.save(account);
        markInbox(inboxEntry, "COMPLETED");

        log.info("Debit OK orderId={} amount={} newBalance={}",
                event.getOrderId(), event.getAmount(), account.getBalance());
        return PaymentResult.success(
                event.getOrderId(), event.getUserId(), event.getAmount(), account.getBalance());
    }

    private void markInbox(PaymentInboxEntry entry, String status) {
        entry.setStatus(status);
        entry.setProcessedAt(LocalDateTime.now());
        inboxRepository.save(entry);
    }
}
