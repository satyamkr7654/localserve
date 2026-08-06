package com.localserve.communication.chat;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import java.time.Clock;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class Conversation {
    private static final Pattern CONTACT = Pattern.compile("(?i)(?:\\+?\\d[\\d -]{7,}\\d|[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,})");
    private final PublicId id;
    private final Set<PublicId> participants;
    private final Set<String> clientMessageIds = new HashSet<>();
    private long nextSequence = 1;
    private boolean bookingConfirmed;

    public Conversation(PublicId id, Set<PublicId> participants, boolean bookingConfirmed) {
        this.id = Objects.requireNonNull(id, "id");
        this.participants = Set.copyOf(participants);
        if (this.participants.size() != 2) throw new IllegalArgumentException("conversation requires two distinct participants");
        this.bookingConfirmed = bookingConfirmed;
    }

    public Message send(PublicId senderId, String clientMessageId, String body, Clock clock) {
        if (!participants.contains(senderId)) throw new DomainException("CHAT.ACCESS_DENIED", "Sender is not a conversation participant");
        if (!clientMessageIds.add(clientMessageId)) throw new DomainException("CHAT.DUPLICATE_MESSAGE", "Message was already accepted");
        if (!bookingConfirmed && CONTACT.matcher(Objects.requireNonNull(body, "body")).find()) {
            clientMessageIds.remove(clientMessageId);
            throw new DomainException("CHAT.CONTACT_SHARING_BLOCKED", "Contact details are available after booking confirmation");
        }
        return new Message(PublicId.generate(), id, senderId, clientMessageId, nextSequence++, body, clock.instant());
    }

    public void confirmBooking() { bookingConfirmed = true; }
}
