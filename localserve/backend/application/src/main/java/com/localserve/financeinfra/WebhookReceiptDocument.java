package com.localserve.financeinfra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("webhook_receipts")
@CompoundIndex(name = "gateway_event_unique", def = "{'provider':1,'providerEventId':1}", unique = true)
public class WebhookReceiptDocument {
    @Id public String id;
    public String provider;
    public String providerEventId;
    public String eventType;
    public Instant providerOccurredAt;
    public Instant receivedAt;
    public String bodySha256;
    public boolean testMode;
    public String processingStatus;
}
