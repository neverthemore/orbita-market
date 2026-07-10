package com.orbita.payments.service;

import java.util.UUID;

/**
 * Result of payment processing — returned by AccountService.processPaymentRequest().
 * Used by PaymentEventConsumer to publish Kafka events AFTER the DB transaction commits.
 *
 * This is the key architectural fix: Kafka events must be published OUTSIDE the
 * @Transactional boundary to avoid a race condition where the consumer in Orders
 * receives OrderPaymentCompleted before the debit is committed to payments_db.
 */
public record PaymentResult(
        ResultType type,
        UUID orderId,
        String userId,
        Long amount,
        Long newBalance,   // set on SUCCESS
        String reason      // set on FAILURE
) {
    public enum ResultType { SUCCESS, FAILURE, DUPLICATE }

    public boolean isSuccess()   { return type == ResultType.SUCCESS; }
    public boolean isDuplicate() { return type == ResultType.DUPLICATE; }

    public static PaymentResult success(UUID orderId, String userId, long amount, long newBalance) {
        return new PaymentResult(ResultType.SUCCESS, orderId, userId, amount, newBalance, null);
    }

    public static PaymentResult failure(UUID orderId, String userId, String reason) {
        return new PaymentResult(ResultType.FAILURE, orderId, userId, null, null, reason);
    }

    public static PaymentResult duplicate() {
        return new PaymentResult(ResultType.DUPLICATE, null, null, null, null, "DUPLICATE_EVENT");
    }
}
