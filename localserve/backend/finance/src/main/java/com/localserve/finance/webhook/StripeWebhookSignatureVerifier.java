package com.localserve.finance.webhook;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StripeWebhookSignatureVerifier implements WebhookSignatureVerifier {
    private final byte[] secret;
    private final Clock clock;
    private final Duration tolerance;

    public StripeWebhookSignatureVerifier(byte[] secret, Clock clock, Duration tolerance) {
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
        if (secret.length < 32 || tolerance.isNegative() || tolerance.isZero() || tolerance.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("invalid Stripe webhook verification configuration");
        }
    }

    @Override
    public boolean verify(RawWebhookRequest request) {
        if (!"STRIPE".equals(request.provider())) {
            return false;
        }
        ParsedSignature parsed = parse(request.headers().get("Stripe-Signature"));
        if (parsed == null) {
            return false;
        }
        Instant signedAt = Instant.ofEpochSecond(parsed.timestamp());
        Duration age = Duration.between(signedAt, clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            return false;
        }
        byte[] prefix = (parsed.timestamp() + ".").getBytes(StandardCharsets.UTF_8);
        byte[] body = request.body();
        byte[] signedPayload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signedPayload, 0, prefix.length);
        System.arraycopy(body, 0, signedPayload, prefix.length, body.length);
        byte[] expected = HmacSupport.sha256(secret, signedPayload);
        return parsed.signatures().stream().anyMatch(signature -> HmacSupport.constantTimeHexEquals(expected, signature));
    }

    private static ParsedSignature parse(String header) {
        if (header == null || header.length() > 2_048) {
            return null;
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        return timestamp == null || signatures.isEmpty() ? null : new ParsedSignature(timestamp, List.copyOf(signatures));
    }

    private record ParsedSignature(long timestamp, List<String> signatures) {
    }
}
