package com.localserve.finance.payment;

public enum PaymentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    CAPTURED,
    HELD,
    RELEASE_PENDING,
    RELEASED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    FAILED,
    CANCELLED,
    DISPUTED,
    FROZEN
}
