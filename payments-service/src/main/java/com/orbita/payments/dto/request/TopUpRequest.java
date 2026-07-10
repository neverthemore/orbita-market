package com.orbita.payments.dto.request;

import jakarta.validation.constraints.Positive;

public record TopUpRequest(
        @Positive(message = "Amount must be greater than zero")
        Long amount
) {}
