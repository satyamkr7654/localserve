package com.localserve.finance.webhook;

import com.localserve.shared.identity.PublicId;

public record WebhookIngressResult(Status status, PublicId receiptId) {
    public enum Status { RECORDED, DUPLICATE }
}
