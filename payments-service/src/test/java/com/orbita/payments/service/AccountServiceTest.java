package com.orbita.payments.service;

import com.orbita.payments.domain.Account;
import com.orbita.payments.domain.PaymentInboxEntry;
import com.orbita.payments.event.OrderPaymentRequestedEvent;
import com.orbita.payments.exception.AccountNotFoundException;
import com.orbita.payments.repository.AccountRepository;
import com.orbita.payments.repository.PaymentInboxRepository;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Feature("Account Service")
class AccountServiceTest {

    @Mock AccountRepository      accountRepository;
    @Mock PaymentInboxRepository inboxRepository;

    // NOTE: no PaymentEventProducer — AccountService no longer calls it directly
    @InjectMocks AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account("user-42");
    }

    // ─── createAccount ────────────────────────────────────────────────────

    @Test
    @DisplayName("createAccount: new user → created=true, balance 0")
    @Story("Account creation")
    void createAccount_newUser() {
        when(accountRepository.findByUserId("user-42")).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenReturn(account);

        AccountCreationResult result = accountService.createAccount("user-42");

        assertThat(result.created()).isTrue();
        assertThat(result.account().getUserId()).isEqualTo("user-42");
        assertThat(result.account().getBalance()).isZero();
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("createAccount: existing user → created=false, returns existing (idempotent)")
    @Story("Account creation")
    void createAccount_existingUser_returnsExisting() {
        when(accountRepository.findByUserId("user-42")).thenReturn(Optional.of(account));

        AccountCreationResult result = accountService.createAccount("user-42");

        assertThat(result.created()).isFalse();
        assertThat(result.account()).isSameAs(account);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAccount: concurrent duplicate insert → catches UNIQUE violation, returns winner's account")
    @Story("Account creation")
    @Description("True idempotency under concurrency: a lost create-race must not surface as an error")
    void createAccount_concurrentRace_returnsWinnerAccount() {
        when(accountRepository.findByUserId("user-42"))
                .thenReturn(Optional.empty())   // first check: not found yet
                .thenReturn(Optional.of(account)); // re-fetch after losing the race: winner's row
        when(accountRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        AccountCreationResult result = accountService.createAccount("user-42");

        assertThat(result.created()).isFalse();
        assertThat(result.account()).isSameAs(account);
    }

    // ─── topUp ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("topUp: adds amount to balance")
    @Story("Balance management")
    void topUp_success() {
        when(accountRepository.findByUserIdWithLock("user-42")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = accountService.topUp("user-42", 1000L);

        assertThat(result.getBalance()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("topUp: account not found → throws AccountNotFoundException")
    @Story("Balance management")
    void topUp_accountNotFound() {
        when(accountRepository.findByUserIdWithLock("user-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.topUp("user-99", 100L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ─── processPaymentRequest ────────────────────────────────────────────

    @Test
    @DisplayName("processPayment: happy path → SUCCESS result, balance debited")
    @Story("Payment processing")
    @Description("Scenario 1: balance 1000, order 120 → SUCCESS, newBalance 880")
    void processPayment_success() {
        account.setBalance(1000L);

        OrderPaymentRequestedEvent event = new OrderPaymentRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "user-42", 120L, Instant.now());

        when(inboxRepository.saveAndFlush(any())).thenReturn(new PaymentInboxEntry());
        when(accountRepository.findByUserIdWithLock("user-42")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResult result = accountService.processPaymentRequest(event);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newBalance()).isEqualTo(880L);
        assertThat(result.amount()).isEqualTo(120L);
        assertThat(account.getBalance()).isEqualTo(880L);

        // Kafka must NOT be called from the service layer
        verifyNoMoreInteractions(/* no producer mock */ accountRepository, inboxRepository);
    }

    @Test
    @DisplayName("processPayment: insufficient balance → FAILURE result, balance unchanged")
    @Story("Payment processing")
    @Description("Scenario 2: balance 50, order 120 → FAILURE INSUFFICIENT_BALANCE, balance 50")
    void processPayment_insufficientBalance() {
        account.setBalance(50L);

        OrderPaymentRequestedEvent event = new OrderPaymentRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "user-42", 120L, Instant.now());

        when(inboxRepository.saveAndFlush(any())).thenReturn(new PaymentInboxEntry());
        when(accountRepository.findByUserIdWithLock("user-42")).thenReturn(Optional.of(account));

        PaymentResult result = accountService.processPaymentRequest(event);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(account.getBalance()).isEqualTo(50L); // unchanged
        verify(accountRepository, never()).save(account); // balance not saved
    }

    @Test
    @DisplayName("processPayment: duplicate event → DUPLICATE result, no DB write to account")
    @Story("Idempotency")
    @Description("Scenario 3: repeat OrderPaymentRequested same event_id → no double charge")
    void processPayment_duplicateEvent_returnsDuplicate() {
        OrderPaymentRequestedEvent event = new OrderPaymentRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "user-42", 120L, Instant.now());

        when(inboxRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate event_id"));

        PaymentResult result = accountService.processPaymentRequest(event);

        assertThat(result.isDuplicate()).isTrue();
        verify(accountRepository, never()).findByUserIdWithLock(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("processPayment: account not found → FAILURE ACCOUNT_NOT_FOUND")
    @Story("Payment processing")
    void processPayment_accountNotFound() {
        OrderPaymentRequestedEvent event = new OrderPaymentRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "user-99", 120L, Instant.now());

        when(inboxRepository.saveAndFlush(any())).thenReturn(new PaymentInboxEntry());
        when(accountRepository.findByUserIdWithLock("user-99")).thenReturn(Optional.empty());

        PaymentResult result = accountService.processPaymentRequest(event);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.reason()).isEqualTo("ACCOUNT_NOT_FOUND");
    }
}
