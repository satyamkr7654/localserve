package com.localserve.finance.webhook;

public interface WebhookReceiptRepository {
    /** @return true when inserted, false when provider/event ID already exists. */
    boolean insertIfAbsent(WebhookReceipt receipt);
}
