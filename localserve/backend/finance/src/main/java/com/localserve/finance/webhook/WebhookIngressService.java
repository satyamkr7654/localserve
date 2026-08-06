package com.localserve.finance.webhook;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

public final class WebhookIngressService {
    private final WebhookSignatureVerifier verifier;
    private final VerifiedWebhookMetadataExtractor metadataExtractor;
    private final WebhookReceiptRepository repository;
    private final Clock clock;

    public WebhookIngressService(WebhookSignatureVerifier verifier,
                                 VerifiedWebhookMetadataExtractor metadataExtractor,
                                 WebhookReceiptRepository repository,
                                 Clock clock) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.metadataExtractor = Objects.requireNonNull(metadataExtractor, "metadataExtractor");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WebhookIngressResult ingest(RawWebhookRequest request) {
        Objects.requireNonNull(request, "request");
        if (!verifier.verify(request)) {
            throw new DomainException("WEBHOOK.SIGNATURE_INVALID", "Webhook signature is invalid");
        }
        byte[] verifiedBody = request.body();
        VerifiedWebhookMetadata metadata = metadataExtractor.extract(request);
        PublicId receiptId = PublicId.generate();
        WebhookReceipt receipt = new WebhookReceipt(receiptId, request.provider(), metadata.eventId(),
                metadata.eventType(), metadata.occurredAt(), clock.instant(), sha256Hex(verifiedBody),
                metadata.testMode(), "RECEIVED");
        boolean inserted = repository.insertIfAbsent(receipt);
        return new WebhookIngressResult(
                inserted ? WebhookIngressResult.Status.RECORDED : WebhookIngressResult.Status.DUPLICATE,
                inserted ? receiptId : null);
    }

    private static String sha256Hex(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
