package com.localserve.communication.chat;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {
    @Test void enforcesMembershipIdempotencyAndContactPolicy() {
        PublicId customer = PublicId.generate(), provider = PublicId.generate();
        var conversation = new Conversation(PublicId.generate(), Set.of(customer, provider), false);
        assertThrows(DomainException.class, () -> conversation.send(customer, "clientmsg1", "call +91 99999 99999", Clock.systemUTC()));
        var message = conversation.send(customer, "clientmsg1", "I am outside", Clock.systemUTC());
        assertEquals(1, message.sequence());
        assertThrows(DomainException.class, () -> conversation.send(customer, "clientmsg1", "again", Clock.systemUTC()));
    }
}
