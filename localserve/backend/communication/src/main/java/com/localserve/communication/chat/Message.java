package com.localserve.communication.chat;

import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;

public record Message(PublicId id, PublicId conversationId, PublicId senderId,
                      String clientMessageId, long sequence, String body, Instant sentAt) {
    public Message {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(senderId, "senderId"); Objects.requireNonNull(sentAt, "sentAt");
        if (clientMessageId == null || !clientMessageId.matches("[A-Za-z0-9_-]{8,100}")) throw new IllegalArgumentException("invalid clientMessageId");
        body = Objects.requireNonNull(body, "body").strip();
        if (body.isBlank() || body.length() > 4_000 || sequence < 1) throw new IllegalArgumentException("invalid message");
    }
}
