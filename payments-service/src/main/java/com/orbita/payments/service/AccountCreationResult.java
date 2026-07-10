package com.orbita.payments.service;

import com.orbita.payments.domain.Account;


public record AccountCreationResult(Account account, boolean created) {}
