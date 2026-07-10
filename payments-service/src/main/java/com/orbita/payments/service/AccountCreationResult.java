package com.orbita.payments.service;

import com.orbita.payments.domain.Account;

/**
 * Result of an idempotent createAccount() call.
 * `created` distinguishes "brand new account" (→ HTTP 201) from
 * "already existed, here it is" (→ HTTP 200) so the controller can
 * return the semantically correct status code.
 */
public record AccountCreationResult(Account account, boolean created) {}
