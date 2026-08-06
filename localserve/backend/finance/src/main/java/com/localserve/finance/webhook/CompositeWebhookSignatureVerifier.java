package com.localserve.finance.webhook;

import java.util.Map;

public final class CompositeWebhookSignatureVerifier implements WebhookSignatureVerifier {
    private final Map<String, WebhookSignatureVerifier> delegates;
    public CompositeWebhookSignatureVerifier(Map<String, WebhookSignatureVerifier> delegates) {
        this.delegates = Map.copyOf(delegates);
    }
    @Override public boolean verify(RawWebhookRequest request) {
        WebhookSignatureVerifier verifier = delegates.get(request.provider());
        return verifier != null && verifier.verify(request);
    }
}
