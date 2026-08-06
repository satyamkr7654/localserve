package com.localserve.financeinfra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.finance.webhook.RawWebhookRequest;
import com.localserve.finance.webhook.VerifiedWebhookMetadata;
import com.localserve.finance.webhook.VerifiedWebhookMetadataExtractor;

import java.io.IOException;
import java.time.Instant;

public final class JacksonWebhookMetadataExtractor implements VerifiedWebhookMetadataExtractor {
    private final ObjectMapper json;
    public JacksonWebhookMetadataExtractor(ObjectMapper json) { this.json = json; }

    @Override public VerifiedWebhookMetadata extract(RawWebhookRequest request) {
        try {
            JsonNode root = json.readTree(request.body());
            if ("STRIPE".equals(request.provider())) {
                long occurred = root.path("created").asLong();
                if (occurred <= 0) throw new IllegalArgumentException("Stripe webhook timestamp is missing");
                return new VerifiedWebhookMetadata(required(root, "id"), required(root, "type"),
                        Instant.ofEpochSecond(occurred), !root.path("livemode").asBoolean(true));
            }
            if ("RAZORPAY".equals(request.provider())) {
                String eventId = request.headers().get("X-Razorpay-Event-Id");
                long occurred = root.path("created_at").asLong();
                if (eventId == null || occurred <= 0) throw new IllegalArgumentException("Razorpay webhook metadata is incomplete");
                return new VerifiedWebhookMetadata(eventId, required(root, "event"), Instant.ofEpochSecond(occurred), false);
            }
            throw new IllegalArgumentException("unsupported webhook provider");
        } catch (IOException malformed) {
            throw new IllegalArgumentException("verified webhook body is malformed", malformed);
        }
    }

    private static String required(JsonNode root, String field) {
        String value = root.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException("webhook field is missing: " + field);
        return value;
    }
}
