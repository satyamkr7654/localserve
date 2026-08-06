package com.localserve.finance.webhook;

import java.time.Instant;
import java.util.Objects;

public record VerifiedWebhookMetadata(String eventId, String eventType, Instant occurredAt, boolean testMode) {
    public VerifiedWebhookMetadata {
        if (eventId == null || eventId.isBlank() || eventId.length() > 255) {
            throw new IllegalArgumentException("eventId is invalid");
        }
        if (eventType == null || eventType.isBlank() || eventType.length() > 255) {
            throw new IllegalArgumentException("eventType is invalid");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
