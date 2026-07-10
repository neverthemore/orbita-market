package com.orbita.orders.exception;
public class UnknownProductTypeException extends RuntimeException {
    public UnknownProductTypeException(String type) { super("Unknown product type: " + type); }
}
