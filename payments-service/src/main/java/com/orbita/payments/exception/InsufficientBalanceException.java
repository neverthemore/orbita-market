package com.orbita.payments.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException() {
        super("Insufficient geocredits balance");
    }
}
