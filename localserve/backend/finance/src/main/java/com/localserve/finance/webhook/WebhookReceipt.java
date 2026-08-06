package com.localserve.finance.webhook;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;

public record WebhookReceipt(
        PublicId id,
        String provider,
        String providerEventId,
        String eventType,
        Instant providerOccurredAt,
        Instant receivedAt,
        String bodySha256,
        boolean testMode,
        String processingStatus) {
    public WebhookReceipt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerEventId, "providerEventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(providerOccurredAt, "providerOccurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(bodySha256, "bodySha256");
        Objects.requireNonNull(processingStatus, "processingStatus");
    }
}
