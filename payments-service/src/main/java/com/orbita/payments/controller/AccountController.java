package com.orbita.payments.controller;

import com.orbita.payments.domain.Account;
import com.orbita.payments.dto.request.TopUpRequest;
import com.orbita.payments.dto.response.AccountResponse;
import com.orbita.payments.dto.response.BalanceResponse;
import com.orbita.payments.exception.MissingUserIdException;
import com.orbita.payments.service.AccountCreationResult;
import com.orbita.payments.service.AccountService;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Feature("Payments API")
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/accounts")
    @Description("Create geocredit account for user")
    public ResponseEntity<AccountResponse> createAccount(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        validateUserId(userId);
        AccountCreationResult result = accountService.createAccount(userId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(AccountResponse.of(result.account()));
    }

    /**
     * POST /payments/accounts/top-up — add geocredits to balance.
     */
    @PostMapping("/accounts/top-up")
    @Description("Top up geocredit balance")
    public ResponseEntity<BalanceResponse> topUp(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody TopUpRequest request) {
        validateUserId(userId);
        Account account = accountService.topUp(userId, request.amount());
        return ResponseEntity.ok(BalanceResponse.of(account));
    }

    /**
     * GET /payments/accounts/balance — return current balance.
     */
    @GetMapping("/accounts/balance")
    @Description("Get current geocredit balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        validateUserId(userId);
        Account account = accountService.getBalance(userId);
        return ResponseEntity.ok(BalanceResponse.of(account));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new MissingUserIdException();
        }
    }
}
