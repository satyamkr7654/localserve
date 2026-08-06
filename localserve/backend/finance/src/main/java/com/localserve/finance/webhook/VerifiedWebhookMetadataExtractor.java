package com.localserve.finance.webhook;

public interface VerifiedWebhookMetadataExtractor {
    VerifiedWebhookMetadata extract(RawWebhookRequest verifiedRequest);
}
