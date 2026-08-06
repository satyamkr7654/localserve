package com.localserve.finance.webhook;

import com.localserve.shared.error.DomainException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookIngressServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-06T12:08:00Z"), ZoneOffset.UTC);

    @Test
    void verifiesBeforeParsingAndDeduplicatesProviderEvent() {
        AtomicInteger extractorCalls = new AtomicInteger();
        Set<String> keys = new HashSet<>();
        WebhookIngressService service = new WebhookIngressService(
                request -> "valid".equals(request.headers().get("signature")),
                verifiedRequest -> {
                    extractorCalls.incrementAndGet();
                    return new VerifiedWebhookMetadata("evt_01", "payment.captured", CLOCK.instant(), true);
                },
                receipt -> keys.add(receipt.provider() + ":" + receipt.providerEventId()),
                CLOCK);
        RawWebhookRequest request = new RawWebhookRequest("RAZORPAY", "{}".getBytes(StandardCharsets.UTF_8), Map.of("signature", "valid"));

        assertThat(service.ingest(request).status()).isEqualTo(WebhookIngressResult.Status.RECORDED);
        assertThat(service.ingest(request).status()).isEqualTo(WebhookIngressResult.Status.DUPLICATE);
        assertThat(extractorCalls).hasValue(2);
    }

    @Test
    void rejectsInvalidSignatureWithoutParsingOrPersistence() {
        AtomicInteger extractorCalls = new AtomicInteger();
        AtomicInteger repositoryCalls = new AtomicInteger();
        WebhookIngressService service = new WebhookIngressService(
                request -> false,
                verifiedRequest -> {
                    extractorCalls.incrementAndGet();
                    return new VerifiedWebhookMetadata("evt_01", "payment.captured", CLOCK.instant(), true);
                },
                receipt -> {
                    repositoryCalls.incrementAndGet();
                    return true;
                }, CLOCK);

        assertThatThrownBy(() -> service.ingest(new RawWebhookRequest("RAZORPAY", new byte[]{1}, Map.of())))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("WEBHOOK.SIGNATURE_INVALID");
        assertThat(extractorCalls).hasValue(0);
        assertThat(repositoryCalls).hasValue(0);
    }
}
