package com.orbita.orders.exception;
public class InvalidPriceException extends RuntimeException {
    public InvalidPriceException() { super("price must be greater than zero"); }
}
