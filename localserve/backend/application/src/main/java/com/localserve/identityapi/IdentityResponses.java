package com.localserve.identityapi;

import java.time.Instant;
import java.util.List;
import java.util.Set;

final class IdentityResponses {
    private IdentityResponses() { }

    static AccountView account(IdentityPersistence.Account account) {
        return new AccountView(account.id, account.displayName, account.normalizedEmail,
                account.normalizedPhone, account.emailVerified, account.phoneVerified,
                account.roles, account.permissions, account.activeRole, account.status,
                account.locale, account.timeZone, account.businessDisplayName,
                account.primaryServiceZoneId, account.providerOnboardingStatus, account.createdAt);
    }

    static SessionView session(IdentityPersistence.DeviceSession session, String currentSessionId) {
        return new SessionView(session.id, session.deviceId, session.deviceName, session.platform,
                session.browserOrApp, session.approximateRegion, session.createdAt, session.lastSeenAt,
                session.remembered, session.riskStatus, session.id.equals(currentSessionId));
    }

    static TokenView token(AuthenticationService.AuthenticatedSession session) {
        return new TokenView(session.accessToken().token(), "Bearer",
                session.accessToken().expiresInSeconds(), session.accessToken().expiresAt(),
                account(session.account()), session(session.deviceSession(), session.deviceSession().id));
    }

    record AccountView(String id, String displayName, String email, String phone,
                       boolean emailVerified, boolean phoneVerified, Set<String> roles,
                       Set<String> permissions, String activeRole, String status, String locale,
                       String timeZone, String businessDisplayName, String primaryServiceZoneId,
                       String providerOnboardingStatus, Instant createdAt) { }

    record SessionView(String id, String deviceId, String deviceName, String platform,
                       String browserOrApp, String approximateRegion, Instant createdAt,
                       Instant lastSeenAt, boolean remembered, String riskStatus,
                       boolean current) { }

    record TokenView(String accessToken, String tokenType, long expiresInSeconds,
                     Instant expiresAt, AccountView account, SessionView session) { }

    record RegistrationView(AccountView account, List<String> nextSteps,
                            boolean verificationDelivered) { }
}
