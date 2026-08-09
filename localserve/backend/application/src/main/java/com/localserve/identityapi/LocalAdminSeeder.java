package com.localserve.identityapi;

import com.localserve.identity.password.PasswordService;
import com.localserve.shared.identity.PublicId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Component
@Profile("local")
class LocalAdminSeeder implements ApplicationRunner {
    private final IdentityPersistence persistence;
    private final PasswordService passwords;
    private final Clock clock;
    private final String email;
    private final String password;
    private final String totpSecret;

    LocalAdminSeeder(IdentityPersistence persistence, PasswordService passwords, Clock clock,
                     @Value("${LOCAL_ADMIN_EMAIL:}") String email,
                     @Value("${LOCAL_ADMIN_PASSWORD:}") String password,
                     @Value("${LOCAL_ADMIN_TOTP_SECRET:}") String totpSecret) {
        this.persistence = persistence;
        this.passwords = passwords;
        this.clock = clock;
        this.email = email;
        this.password = password;
        this.totpSecret = totpSecret;
    }

    @Override
    public void run(ApplicationArguments ignored) {
        if (email.isBlank() && password.isBlank() && totpSecret.isBlank()) return;
        if (email.isBlank() || password.isBlank() || totpSecret.isBlank()) {
            throw new IllegalStateException("All LOCAL_ADMIN_* values are required when local admin seeding is enabled");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (persistence.findByEmail(normalizedEmail).isPresent()) return;
        Instant now = clock.instant();
        IdentityPersistence.Account account = new IdentityPersistence.Account();
        account.id = PublicId.generate().toString();
        account.displayName = "Local administrator";
        account.normalizedEmail = normalizedEmail;
        account.emailVerified = true;
        account.passwordHash = passwords.hash(password.toCharArray());
        account.roles = Set.of("ADMIN");
        account.permissions = Set.of("providers:review", "bookings:read", "finance:read", "disputes:manage");
        account.activeRole = "ADMIN";
        account.status = "ACTIVE";
        account.locale = "en";
        account.timeZone = "UTC";
        account.acceptedTermsVersion = "internal-admin";
        account.mfaRequired = true;
        account.totpSecretBase32 = totpSecret;
        account.passwordChangedAt = now;
        account.createdAt = now;
        account.updatedAt = now;
        try { persistence.createAccount(account); }
        catch (DuplicateKeyException ignoredDuplicate) { /* another local instance won the seed race */ }
    }
}
