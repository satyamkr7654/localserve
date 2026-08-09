package com.localserve.identityapi;

import com.localserve.identity.otp.OtpDelivery;
import com.localserve.identity.otp.OtpPurpose;
import com.localserve.identity.otp.OtpService;
import com.localserve.identity.password.PasswordService;
import com.localserve.identity.session.IssuedRefreshToken;
import com.localserve.identity.session.RefreshTokenService;
import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthenticationService {
    private static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    private static final String PASSWORD_RECOVERY = "PASSWORD_RECOVERY";
    private final IdentityPersistence persistence;
    private final PasswordService passwords;
    private final OtpService otps;
    private final RefreshTokenService refreshTokens;
    private final JwtTokenService accessTokens;
    private final OneTimeActionService actions;
    private final AuthDeliveryService delivery;
    private final AdminMfaService adminMfa;
    private final Clock clock;
    private final byte[] subjectPepper;

    public AuthenticationService(IdentityPersistence persistence, PasswordService passwords, OtpService otps,
                                 RefreshTokenService refreshTokens, JwtTokenService accessTokens,
                                 OneTimeActionService actions, AuthDeliveryService delivery,
                                 AdminMfaService adminMfa, Clock clock,
                                 @org.springframework.beans.factory.annotation.Value("${OTP_HMAC_PEPPER}") String subjectPepper) {
        this.persistence = persistence;
        this.passwords = passwords;
        this.otps = otps;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.actions = actions;
        this.delivery = delivery;
        this.adminMfa = adminMfa;
        this.clock = clock;
        this.subjectPepper = subjectPepper.getBytes(StandardCharsets.UTF_8);
    }

    public Registration registerCustomer(RegistrationCommand command, RequestContext context) {
        return register(command, Set.of("CUSTOMER"), "CUSTOMER", context);
    }

    public Registration registerProvider(RegistrationCommand command, RequestContext context) {
        if (command.businessDisplayName() == null || command.businessDisplayName().isBlank()) {
            throw new DomainException("AUTH.PROVIDER_PROFILE_REQUIRED", "A business display name is required");
        }
        return register(command, Set.of("CUSTOMER", "PROVIDER"), "PROVIDER", context);
    }

    private Registration register(RegistrationCommand command, Set<String> roles, String activeRole,
                                  RequestContext context) {
        String email = normalizeEmail(command.email());
        String phone = command.phone() == null || command.phone().isBlank() ? null : normalizePhone(command.phone());
        Instant now = clock.instant();
        IdentityPersistence.Account account = new IdentityPersistence.Account();
        account.id = PublicId.generate().toString();
        account.displayName = requireText(command.displayName(), 2, 80, "display name");
        account.normalizedEmail = email;
        account.normalizedPhone = phone;
        account.passwordHash = passwords.hash(command.password().toCharArray());
        account.roles = roles;
        account.permissions = Set.of();
        account.activeRole = activeRole;
        account.status = "ACTIVE";
        account.locale = safeLocale(command.locale());
        account.timeZone = requireText(command.timeZone(), 1, 64, "time zone");
        account.acceptedTermsVersion = requireText(command.acceptedTermsVersion(), 1, 40, "terms version");
        account.marketingConsent = command.marketingConsent();
        account.businessDisplayName = command.businessDisplayName();
        account.primaryServiceZoneId = command.primaryServiceZoneId();
        account.providerOnboardingStatus = roles.contains("PROVIDER") ? "DRAFT" : null;
        account.passwordChangedAt = now;
        account.createdAt = now;
        account.updatedAt = now;
        try {
            persistence.createAccount(account);
        } catch (DuplicateKeyException duplicate) {
            throw new DomainException("AUTH.IDENTITY_ALREADY_EXISTS", "An account already exists for this identity");
        }

        boolean delivered = sendEmailVerification(account);
        persistence.recordActivity(account, null, "REGISTRATION", "SUCCEEDED", null,
                context.approximateRegion(), context.correlationId());
        return new Registration(account, delivered);
    }

    public LoginOutcome passwordLogin(String login, String password, boolean rememberMe,
                                      IdentityPersistence.DeviceInput device, boolean admin,
                                      String requiredRole, RequestContext context) {
        String normalized = login != null && login.trim().startsWith("+")
                ? normalizePhone(login) : normalizeEmail(login);
        IdentityPersistence.Account account = persistence.findByLogin(normalized).orElse(null);
        boolean matches = passwords.matches(password.toCharArray(), account == null ? null : account.passwordHash);
        String normalizedRole = requiredRole == null ? (admin ? "ADMIN" : "CUSTOMER") : requiredRole;
        if (!Set.of("CUSTOMER", "PROVIDER", "ADMIN").contains(normalizedRole)
                || !matches || account == null || account.passwordHash == null || account.isAdmin() != admin
                || !account.roles.contains(normalizedRole)) {
            persistence.recordActivity(account, null, "PASSWORD_LOGIN", "FAILED", "INVALID_CREDENTIALS",
                    context.approximateRegion(), context.correlationId());
            throw invalidCredentials();
        }
        requireUsable(account);
        if ((!admin && normalized.contains("@") && !account.emailVerified)
                || (!admin && normalized.startsWith("+") && !account.phoneVerified)) {
            persistence.recordActivity(account, null, "PASSWORD_LOGIN", "FAILED", "CONTACT_NOT_VERIFIED",
                    context.approximateRegion(), context.correlationId());
            throw new DomainException("AUTH.CONTACT_NOT_VERIFIED", "Verify this contact method before signing in");
        }
        if (admin && account.mfaRequired) {
            AdminMfaService.Challenge challenge = adminMfa.issue(account);
            return LoginOutcome.mfa(challenge);
        }
        return LoginOutcome.session(startSession(account, rememberMe && !admin, device, context, null));
    }

    public AuthenticatedSession verifyAdminMfa(PublicId challengeId, String code,
                                               IdentityPersistence.DeviceInput device,
                                               RequestContext context) {
        return verifyMfa(challengeId, code, device, "ADMIN", context);
    }

    public AuthenticatedSession verifyMfa(PublicId challengeId, String code,
                                          IdentityPersistence.DeviceInput device, String requiredRole,
                                          RequestContext context) {
        IdentityPersistence.Account account = adminMfa.verify(challengeId, code, persistence);
        requireUsable(account);
        if (!account.roles.contains(requiredRole) || account.isAdmin() != "ADMIN".equals(requiredRole)) {
            throw invalidCredentials();
        }
        return startSession(account, false, device, context, clock.instant());
    }

    public AdminMfaService.Challenge requestAdminStepUp(PublicId principalId) {
        IdentityPersistence.Account account = requireAccount(principalId);
        requireUsable(account);
        if (!account.isAdmin() || !account.mfaRequired) {
            throw new DomainException("AUTH.MFA_REQUIRED", "An enrolled administrator MFA method is required");
        }
        return adminMfa.issue(account);
    }

    public JwtTokenService.IssuedAccessToken verifyAdminStepUp(PublicId challengeId, String code,
                                                               PublicId sessionId) {
        IdentityPersistence.Account account = adminMfa.verify(challengeId, code, persistence);
        IdentityPersistence.DeviceSession session = persistence.findSession(sessionId)
                .filter(IdentityPersistence.DeviceSession::active)
                .orElseThrow(() -> new DomainException("AUTH.SESSION_REVOKED", "Session has been revoked"));
        if (!account.isAdmin() || !account.id.equals(session.principalId)) {
            throw new DomainException("ACCESS.DENIED", "The MFA challenge is not bound to this session");
        }
        return accessTokens.issue(account, sessionId, session.createdAt, clock.instant());
    }

    public OtpIssue issuePhoneOtp(String rawPhone, OtpPurpose purpose) {
        if (purpose != OtpPurpose.LOGIN && purpose != OtpPurpose.REGISTER && purpose != OtpPurpose.VERIFY_PHONE) {
            throw new DomainException("AUTH.OTP_PURPOSE_INVALID", "OTP purpose is not supported by this endpoint");
        }
        String phone = normalizePhone(rawPhone);
        IdentityPersistence.Account account = persistence.findByPhone(phone).orElse(null);
        long version = account == null || account.version == null ? 0 : account.version;
        OtpDelivery issued = otps.issue(subjectHash(phone), purpose, version);
        if (account != null || purpose != OtpPurpose.LOGIN) {
            delivery.sendOtp(phone, issued.plaintextCode(), issued.expiresAt());
        }
        return new OtpIssue(issued.challengeId(), issued.expiresAt(), version);
    }

    public PhoneOtpResult verifyPhoneOtp(PublicId challengeId, String rawPhone, OtpPurpose purpose,
                                         String code, long issuanceVersion, boolean rememberMe,
                                         IdentityPersistence.DeviceInput device, RequestContext context) {
        String phone = normalizePhone(rawPhone);
        otps.verify(challengeId, subjectHash(phone), purpose, code, issuanceVersion);
        IdentityPersistence.Account account = persistence.findByPhone(phone).orElseThrow(AuthenticationService::invalidCredentials);
        requireUsable(account);
        if (purpose == OtpPurpose.VERIFY_PHONE || purpose == OtpPurpose.REGISTER) {
            account.phoneVerified = true;
            persistence.saveAccount(account);
        }
        if (purpose == OtpPurpose.VERIFY_PHONE) return new PhoneOtpResult(account, null);
        return new PhoneOtpResult(account, startSession(account, rememberMe, device, context, null));
    }

    public void requestEmailVerification(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        persistence.findByEmail(email).filter(account -> !account.emailVerified).ifPresent(this::sendEmailVerification);
    }

    public IdentityPersistence.Account verifyEmail(String token) {
        OneTimeActionService.Action action = actions.consume(token, EMAIL_VERIFICATION);
        IdentityPersistence.Account account = requireAccount(PublicId.parse(action.principalId()));
        if (!action.subject().equals(account.normalizedEmail)) throw invalidCredentials();
        account.emailVerified = true;
        return persistence.saveAccount(account);
    }

    public void requestPasswordRecovery(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        persistence.findByEmail(email).filter(IdentityPersistence.Account::isActive).ifPresent(account -> {
            String token = actions.issue(PASSWORD_RECOVERY, account.id, account.normalizedEmail, Duration.ofMinutes(20));
            delivery.sendPasswordRecovery(account.normalizedEmail, token);
        });
    }

    public void resetPassword(String token, String newPassword) {
        OneTimeActionService.Action action = actions.consume(token, PASSWORD_RECOVERY);
        IdentityPersistence.Account account = requireAccount(PublicId.parse(action.principalId()));
        if (!action.subject().equals(account.normalizedEmail)) throw invalidCredentials();
        account.passwordHash = passwords.hash(newPassword.toCharArray());
        account.passwordChangedAt = clock.instant();
        persistence.saveAccount(account);
        revokeAll(account.publicId(), "PASSWORD_CHANGED");
    }

    public AuthenticatedSession refresh(PublicId sessionId, String presentedToken, RequestContext context) {
        IdentityPersistence.DeviceSession session = persistence.findSession(sessionId)
                .filter(IdentityPersistence.DeviceSession::active)
                .orElseThrow(() -> new DomainException("AUTH.SESSION_REVOKED", "Session has been revoked"));
        IdentityPersistence.Account account = requireAccount(PublicId.parse(session.principalId));
        requireUsable(account);
        try {
            IssuedRefreshToken refresh = refreshTokens.rotate(presentedToken, sessionId);
            persistence.touchSession(session);
            JwtTokenService.IssuedAccessToken access = accessTokens.issue(account, sessionId, session.createdAt, null);
            return new AuthenticatedSession(account, session, access, refresh);
        } catch (DomainException error) {
            if ("AUTH.REFRESH_REUSE_DETECTED".equals(error.code())) {
                persistence.revokeSession(session, "REFRESH_REUSE_DETECTED");
                persistence.recordActivity(account, session, "TOKEN_REFRESH", "FAILED", "REFRESH_REUSE_DETECTED",
                        context.approximateRegion(), context.correlationId());
            }
            throw error;
        }
    }

    public AuthenticatedSession startOAuthSession(PublicId principalId, IdentityPersistence.DeviceInput device,
                                                  RequestContext context) {
        IdentityPersistence.Account account = requireAccount(principalId);
        requireUsable(account);
        return startSession(account, false, device, context, null);
    }

    public void logout(PublicId sessionId) { revokeSession(sessionId, null, "USER_LOGOUT"); }

    public void revokeOwnedSession(PublicId sessionId, PublicId principalId) {
        revokeSession(sessionId, principalId, "USER_REVOKED");
    }

    public void revokeAll(PublicId principalId, String reason) {
        for (IdentityPersistence.DeviceSession session : persistence.activeSessions(principalId)) {
            refreshTokens.revokeSession(session.publicId(), reason);
            persistence.revokeSession(session, reason);
        }
    }

    public void revokeOtherSessions(PublicId principalId, PublicId currentSessionId, String reason) {
        for (IdentityPersistence.DeviceSession session : persistence.activeSessions(principalId)) {
            if (!session.id.equals(currentSessionId.toString())) {
                refreshTokens.revokeSession(session.publicId(), reason);
                persistence.revokeSession(session, reason);
            }
        }
    }

    public IdentityPersistence.Account requireAccount(PublicId id) {
        return persistence.findAccount(id).orElseThrow(() -> new DomainException("AUTH.ACCOUNT_NOT_FOUND", "Account was not found"));
    }

    public java.util.List<IdentityPersistence.DeviceSession> sessions(PublicId principalId) {
        return persistence.activeSessions(principalId);
    }

    public java.util.List<IdentityPersistence.AuthActivity> activity(PublicId principalId, int limit) {
        return persistence.activity(principalId, limit);
    }

    private AuthenticatedSession startSession(IdentityPersistence.Account account, boolean rememberMe,
                                              IdentityPersistence.DeviceInput device, RequestContext context,
                                              Instant stepUpAt) {
        IdentityPersistence.DeviceSession session = persistence.createSession(account.publicId(), device,
                rememberMe, context.approximateRegion());
        IssuedRefreshToken refresh = refreshTokens.issue(session.publicId(), account.publicId(), rememberMe);
        JwtTokenService.IssuedAccessToken access = accessTokens.issue(account, session.publicId(), clock.instant(), stepUpAt);
        persistence.recordActivity(account, session, "LOGIN", "SUCCEEDED", null,
                context.approximateRegion(), context.correlationId());
        return new AuthenticatedSession(account, session, access, refresh);
    }

    private void revokeSession(PublicId sessionId, PublicId owner, String reason) {
        IdentityPersistence.DeviceSession session = persistence.findSession(sessionId)
                .orElseThrow(() -> new DomainException("AUTH.SESSION_NOT_FOUND", "Session was not found"));
        if (owner != null && !owner.toString().equals(session.principalId)) {
            throw new DomainException("ACCESS.DENIED", "The session does not belong to this account");
        }
        refreshTokens.revokeSession(sessionId, reason);
        persistence.revokeSession(session, reason);
    }

    private boolean sendEmailVerification(IdentityPersistence.Account account) {
        if (account.normalizedEmail == null || account.emailVerified) return false;
        String token = actions.issue(EMAIL_VERIFICATION, account.id, account.normalizedEmail, Duration.ofHours(24));
        delivery.sendEmailVerification(account.normalizedEmail, token);
        return true;
    }

    private static void requireUsable(IdentityPersistence.Account account) {
        if (!account.isActive()) {
            throw new DomainException("AUTH.ACCOUNT_UNAVAILABLE", "This account is not available for sign-in");
        }
    }

    private String subjectHash(String subject) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(subjectPepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static String normalizeEmail(String value) {
        String email = requireText(value, 3, 254, "email").toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("invalid email");
        return email;
    }

    private static String normalizePhone(String value) {
        String phone = value == null ? "" : value.replaceAll("[\\s()-]", "");
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) throw new IllegalArgumentException("invalid E.164 phone");
        return phone;
    }

    private static String safeLocale(String locale) {
        String value = requireText(locale, 2, 20, "locale");
        if (!value.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) throw new IllegalArgumentException("invalid locale");
        return value;
    }

    private static String requireText(String value, int min, int max, String field) {
        if (value == null || value.isBlank() || value.length() < min || value.length() > max) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return value.trim();
    }

    private static DomainException invalidCredentials() {
        return new DomainException("AUTH.INVALID_CREDENTIALS", "The supplied credentials are invalid");
    }

    public record RegistrationCommand(String displayName, String email, String phone, String password,
                                      String locale, String timeZone, String acceptedTermsVersion,
                                      boolean marketingConsent, String businessDisplayName,
                                      String primaryServiceZoneId) { }
    public record Registration(IdentityPersistence.Account account, boolean verificationDelivered) { }
    public record AuthenticatedSession(IdentityPersistence.Account account,
                                       IdentityPersistence.DeviceSession deviceSession,
                                       JwtTokenService.IssuedAccessToken accessToken,
                                       IssuedRefreshToken refreshToken) { }
    public record PhoneOtpResult(IdentityPersistence.Account account, AuthenticatedSession session) { }
    public record OtpIssue(PublicId challengeId, Instant expiresAt, long issuanceVersion) { }
    public record RequestContext(String approximateRegion, String correlationId) { }
    public record LoginOutcome(AuthenticatedSession session, AdminMfaService.Challenge mfaChallenge) {
        static LoginOutcome session(AuthenticatedSession session) { return new LoginOutcome(session, null); }
        static LoginOutcome mfa(AdminMfaService.Challenge challenge) { return new LoginOutcome(null, challenge); }
        public boolean requiresMfa() { return mfaChallenge != null; }
    }
}
