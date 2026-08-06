package com.localserve.shared.identity;

import java.util.Objects;
import java.util.UUID;

public record PublicId(UUID value) implements Comparable<PublicId> {
    private static final UuidV7Generator GENERATOR = new UuidV7Generator();

    public PublicId {
        Objects.requireNonNull(value, "value");
        if (value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException("public identifiers must be RFC 9562 UUIDv7 values");
        }
    }

    public static PublicId generate() {
        return new PublicId(GENERATOR.generate());
    }

    public static PublicId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (!raw.equals(raw.toLowerCase())) {
            throw new IllegalArgumentException("public identifier must be lowercase canonical UUID text");
        }
        UUID parsed = UUID.fromString(raw);
        if (!parsed.toString().equals(raw)) {
            throw new IllegalArgumentException("public identifier must be canonical UUID text");
        }
        return new PublicId(parsed);
    }

    @Override
    public int compareTo(PublicId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
