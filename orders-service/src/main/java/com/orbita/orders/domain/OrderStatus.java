package com.orbita.orders.domain;

/**
 * Life cycle of an order:
 *
 *  CREATED → PAYMENT_PENDING → PAID
 *                           ↘ PAYMENT_FAILED
 *  CREATED → REJECTED  (invalid payload / price)
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    REJECTED
}
