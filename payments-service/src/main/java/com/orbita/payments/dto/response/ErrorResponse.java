package com.orbita.payments.dto.response;

import java.time.Instant;

public record ErrorResponse(
        String error_code,
        String message,
        String timestamp
) {
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, Instant.now().toString());
    }
}
