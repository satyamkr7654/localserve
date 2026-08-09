package com.localserve.identity.password;

import com.localserve.shared.error.DomainException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

public final class PasswordService {
    private final PasswordEncoder encoder;
    private final BreachedPasswordChecker breachedPasswordChecker;
    private final String dummyHash;

    public PasswordService(BreachedPasswordChecker breachedPasswordChecker) {
        this(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(), breachedPasswordChecker);
    }

    PasswordService(PasswordEncoder encoder, BreachedPasswordChecker breachedPasswordChecker) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.breachedPasswordChecker = Objects.requireNonNull(breachedPasswordChecker, "breachedPasswordChecker");
        this.dummyHash = encoder.encode("localserve-enumeration-safe-dummy-password");
    }

    public String hash(char[] password) {
        validate(password);
        try {
            if (breachedPasswordChecker.isKnownBreached(password)) {
                throw new DomainException("AUTH.PASSWORD_BREACHED", "Choose a password that has not appeared in known breaches");
            }
            return encoder.encode(new String(password));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public boolean matches(char[] password, String encoded) {
        Objects.requireNonNull(password, "password");
        try {
            return encoder.matches(new String(password), encoded == null ? dummyHash : encoded) && encoded != null;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public boolean needsUpgrade(String encoded) {
        return encoder.upgradeEncoding(encoded);
    }

    private static void validate(char[] password) {
        Objects.requireNonNull(password, "password");
        if (password.length < 12 || password.length > 128) {
            throw new DomainException("AUTH.PASSWORD_POLICY_FAILED", "Password must contain 12 to 128 characters");
        }
    }
}
