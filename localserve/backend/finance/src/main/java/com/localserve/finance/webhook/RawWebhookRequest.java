package com.localserve.finance.webhook;

import java.util.Map;
import java.util.Objects;

public record RawWebhookRequest(String provider, byte[] body, Map<String, String> headers) {
    public RawWebhookRequest {
        Objects.requireNonNull(provider, "provider");
        body = Objects.requireNonNull(body, "body").clone();
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        if (body.length == 0 || body.length > 1_048_576) {
            throw new IllegalArgumentException("webhook body must be between 1 byte and 1 MiB");
        }
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
