package com.localserve.finance.webhook;

public interface WebhookSignatureVerifier {
    boolean verify(RawWebhookRequest request);
}
