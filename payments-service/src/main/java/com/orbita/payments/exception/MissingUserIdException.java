package com.orbita.payments.exception;

public class MissingUserIdException extends RuntimeException {
    public MissingUserIdException() {
        super("X-User-Id header is required");
    }
}
