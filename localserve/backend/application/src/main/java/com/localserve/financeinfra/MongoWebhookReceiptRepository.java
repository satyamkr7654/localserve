package com.localserve.financeinfra;

import com.localserve.finance.webhook.WebhookReceipt;
import com.localserve.finance.webhook.WebhookReceiptRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MongoWebhookReceiptRepository implements WebhookReceiptRepository {
    private final MongoTemplate mongo;
    public MongoWebhookReceiptRepository(MongoTemplate mongo) { this.mongo = mongo; }

    @Override public boolean insertIfAbsent(WebhookReceipt source) {
        var target = new WebhookReceiptDocument();
        target.id = source.id().toString(); target.provider = source.provider();
        target.providerEventId = source.providerEventId(); target.eventType = source.eventType();
        target.providerOccurredAt = source.providerOccurredAt(); target.receivedAt = source.receivedAt();
        target.bodySha256 = source.bodySha256(); target.testMode = source.testMode();
        target.processingStatus = source.processingStatus();
        try { mongo.insert(target); return true; }
        catch (DuplicateKeyException duplicate) { return false; }
    }
}
