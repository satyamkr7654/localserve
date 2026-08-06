package com.localserve.shared.idempotency;

import java.util.Objects;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.length() < 16 || value.length() > 128 || !value.matches("[\\x21-\\x7E]+")) {
            throw new IllegalArgumentException("idempotency key must contain 16-128 printable ASCII characters");
        }
    }
}
