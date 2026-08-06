package com.localserve.identity.otp;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceTest {
    @Test
    void verifiesOnceAndRejectsReplayOrWrongPurpose() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T12:08:00Z"), ZoneOffset.UTC);
        InMemoryStore store = new InMemoryStore();
        OtpService service = new OtpService(store, clock, new SecureRandom(new byte[]{1, 2, 3}),
                new byte[32], Duration.ofMinutes(5), 5);
        OtpDelivery delivery = service.issue("a".repeat(64), OtpPurpose.LOGIN, 2);

        service.verify(delivery.challengeId(), "a".repeat(64), OtpPurpose.LOGIN,
                delivery.plaintextCode(), 2);
        assertThatThrownBy(() -> service.verify(delivery.challengeId(), "a".repeat(64), OtpPurpose.LOGIN,
                delivery.plaintextCode(), 2))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo("AUTH.OTP_REPLAYED");
    }

    private static final class InMemoryStore implements OtpChallengeStore {
        private final Map<PublicId, State> states = new HashMap<>();

        @Override
        public synchronized void create(OtpChallenge challenge) {
            if (states.putIfAbsent(challenge.id(), new State(challenge)) != null) {
                throw new IllegalStateException("duplicate challenge");
            }
        }

        @Override
        public synchronized OtpAttemptResult attempt(PublicId id, OtpPurpose purpose, String subjectHash,
                                                     String candidateHash, Instant attemptedAt) {
            State state = states.get(id);
            if (state == null) return OtpAttemptResult.NOT_FOUND;
            if (state.consumed) return OtpAttemptResult.CONSUMED;
            if (!state.challenge.purpose().equals(purpose) || !state.challenge.subjectHash().equals(subjectHash))
                return OtpAttemptResult.PURPOSE_OR_SUBJECT_MISMATCH;
            if (!attemptedAt.isBefore(state.challenge.expiresAt())) return OtpAttemptResult.EXPIRED;
            if (state.attempts >= state.challenge.maxAttempts()) return OtpAttemptResult.LOCKED;
            state.attempts++;
            if (!MessageDigest.isEqual(state.challenge.codeHash().getBytes(), candidateHash.getBytes())) {
                return state.attempts >= state.challenge.maxAttempts() ? OtpAttemptResult.LOCKED : OtpAttemptResult.INVALID;
            }
            state.consumed = true;
            return OtpAttemptResult.VERIFIED;
        }

        private static final class State {
            private final OtpChallenge challenge;
            private int attempts;
            private boolean consumed;
            private State(OtpChallenge challenge) { this.challenge = challenge; }
        }
    }
}
