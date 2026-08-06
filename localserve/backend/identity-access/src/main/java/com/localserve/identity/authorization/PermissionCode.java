package com.localserve.identity.authorization;

import java.util.Objects;

public record PermissionCode(String value) {
    public PermissionCode {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+")) {
            throw new IllegalArgumentException("permission code has an invalid format");
        }
    }
}
