package com.localserve.identityapi;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;

@Service
public class AdminMfaService {
    private static final String PREFIX = "localserve:admin-mfa:";
    private final StringRedisTemplate redis;
    private final Clock clock;

    public AdminMfaService(StringRedisTemplate redis, Clock clock) { this.redis = redis; this.clock = clock; }

    public Challenge issue(IdentityPersistence.Account account) {
        PublicId id = PublicId.generate();
        redis.opsForValue().set(PREFIX + id, account.id, Duration.ofMinutes(5));
        return new Challenge(id, clock.instant().plus(Duration.ofMinutes(5)));
    }

    public IdentityPersistence.Account verify(PublicId challengeId, String code, IdentityPersistence persistence) {
        String accountId = redis.opsForValue().getAndDelete(PREFIX + challengeId);
        if (accountId == null) throw invalid();
        IdentityPersistence.Account account = persistence.findAccount(PublicId.parse(accountId))
                .orElseThrow(AdminMfaService::invalid);
        if (account.totpSecretBase32 == null || !validTotp(account.totpSecretBase32, code, clock.millis())) throw invalid();
        return account;
    }

    static boolean validTotp(String base32Secret, String code, long epochMillis) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        byte[] secret = decodeBase32(base32Secret);
        long counter = epochMillis / 30_000L;
        for (long candidate = counter - 1; candidate <= counter + 1; candidate++) {
            if (totp(secret, candidate).equals(code)) return true;
        }
        return false;
    }

    private static String totp(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24) | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8) | (digest[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP algorithm is unavailable", exception);
        }
    }

    private static byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String normalized = value.replace("=", "").replaceAll("\\s", "").toUpperCase();
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0, bits = 0, index = 0;
        for (char character : normalized.toCharArray()) {
            int digit = alphabet.indexOf(character);
            if (digit < 0) throw invalid();
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                output[index++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return output;
    }

    private static DomainException invalid() {
        return new DomainException("AUTH.MFA_INVALID", "Multi-factor verification is invalid or expired");
    }

    public record Challenge(PublicId id, java.time.Instant expiresAt) { }
}
