package com.localserve.finance.webhook;

import java.util.Objects;

public final class RazorpayWebhookSignatureVerifier implements WebhookSignatureVerifier {
    private final byte[] secret;

    public RazorpayWebhookSignatureVerifier(byte[] secret) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        if (secret.length < 32) {
            throw new IllegalArgumentException("webhook secret must contain at least 256 bits");
        }
    }

    @Override
    public boolean verify(RawWebhookRequest request) {
        if (!"RAZORPAY".equals(request.provider())) {
            return false;
        }
        String signature = request.headers().get("X-Razorpay-Signature");
        return HmacSupport.constantTimeHexEquals(HmacSupport.sha256(secret, request.body()), signature);
    }
}
