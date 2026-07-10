package com.orbita.payments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbita.payments.domain.Account;
import com.orbita.payments.dto.request.TopUpRequest;
import com.orbita.payments.exception.AccountNotFoundException;
import com.orbita.payments.service.AccountCreationResult;
import com.orbita.payments.service.AccountService;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Feature("Payments API")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "orbita.kafka.topics.payment-requested=order-payment-requested",
    "orbita.kafka.topics.payment-completed=order-payment-completed",
    "orbita.kafka.topics.payment-failed=order-payment-failed"
})
class AccountControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @MockBean  AccountService accountService;

    /** Build an Account using Lombok setters — no reflection needed. */
    private Account buildAccount(String userId, long balance) {
        Account a = new Account(userId);
        a.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        a.setBalance(balance);
        return a;
    }

    // ─── POST /accounts ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /accounts → 201 Created when account is brand new")
    @Story("Account creation")
    @Description("Happy path: create account for new user")
    void createAccount_newAccount_returns201() throws Exception {
        Account account = buildAccount("user-42", 0L);
        when(accountService.createAccount("user-42"))
                .thenReturn(new AccountCreationResult(account, true));

        mockMvc.perform(post("/api/v1/payments/accounts")
                        .header("X-User-Id", "user-42"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.currency").value("geocredits"))
                .andExpect(jsonPath("$.accountId").value("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    @DisplayName("POST /accounts → 200 OK when account already existed (idempotent replay)")
    @Story("Account creation")
    @Description("Repeat call for the same user must not error, and must signal 'nothing new happened' via 200")
    void createAccount_existingAccount_returns200() throws Exception {
        Account account = buildAccount("user-42", 5000L);
        when(accountService.createAccount("user-42"))
                .thenReturn(new AccountCreationResult(account, false));

        mockMvc.perform(post("/api/v1/payments/accounts")
                        .header("X-User-Id", "user-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.balance").value(5000));
    }

    @Test
    @DisplayName("POST /accounts without X-User-Id → 400 MISSING_USER_ID")
    @Story("Validation")
    void createAccount_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("MISSING_USER_ID"));
    }

    // ─── POST /accounts/top-up ────────────────────────────────────────────

    @Test
    @DisplayName("POST /accounts/top-up → 200 with updated balance")
    @Story("Balance top-up")
    @Description("Happy path: add 5000 geocredits")
    void topUp_success() throws Exception {
        Account account = buildAccount("user-42", 5000L);
        when(accountService.topUp(eq("user-42"), eq(5000L))).thenReturn(account);

        String body = objectMapper.writeValueAsString(new TopUpRequest(5000L));
        mockMvc.perform(post("/api/v1/payments/accounts/top-up")
                        .header("X-User-Id", "user-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000))
                .andExpect(jsonPath("$.currency").value("geocredits"));
    }

    @Test
    @DisplayName("POST /accounts/top-up with amount=0 → 400 INVALID_AMOUNT")
    @Story("Validation")
    void topUp_zeroAmount_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new TopUpRequest(0L));
        mockMvc.perform(post("/api/v1/payments/accounts/top-up")
                        .header("X-User-Id", "user-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_AMOUNT"));
    }

    @Test
    @DisplayName("POST /accounts/top-up with negative amount → 400 INVALID_AMOUNT")
    @Story("Validation")
    void topUp_negativeAmount_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(new TopUpRequest(-100L));
        mockMvc.perform(post("/api/v1/payments/accounts/top-up")
                        .header("X-User-Id", "user-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_AMOUNT"));
    }

    @Test
    @DisplayName("POST /accounts/top-up — account not found → 404 ACCOUNT_NOT_FOUND")
    @Story("Balance top-up")
    void topUp_accountNotFound_returns404() throws Exception {
        when(accountService.topUp(any(), anyLong()))
                .thenThrow(new AccountNotFoundException("user-99"));

        String body = objectMapper.writeValueAsString(new TopUpRequest(100L));
        mockMvc.perform(post("/api/v1/payments/accounts/top-up")
                        .header("X-User-Id", "user-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_NOT_FOUND"));
    }

    // ─── GET /accounts/balance ────────────────────────────────────────────

    @Test
    @DisplayName("GET /accounts/balance → 200 with correct balance")
    @Story("Balance query")
    @Description("Happy path: query current balance")
    void getBalance_success() throws Exception {
        Account account = buildAccount("user-42", 880L);
        when(accountService.getBalance("user-42")).thenReturn(account);

        mockMvc.perform(get("/api/v1/payments/accounts/balance")
                        .header("X-User-Id", "user-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.balance").value(880))
                .andExpect(jsonPath("$.currency").value("geocredits"));
    }

    @Test
    @DisplayName("GET /accounts/balance — account not found → 404")
    @Story("Balance query")
    void getBalance_notFound_returns404() throws Exception {
        when(accountService.getBalance("user-99"))
                .thenThrow(new AccountNotFoundException("user-99"));

        mockMvc.perform(get("/api/v1/payments/accounts/balance")
                        .header("X-User-Id", "user-99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /accounts/balance without X-User-Id → 400")
    @Story("Validation")
    void getBalance_missingUserId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/payments/accounts/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("MISSING_USER_ID"));
    }
}
