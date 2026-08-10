package com.localserve.identityapi;

import com.localserve.shared.identity.PublicId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class IdentityPersistence {
    private final MongoTemplate mongo;
    private final Clock clock;

    public IdentityPersistence(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    public Account createAccount(Account account) { return mongo.insert(account); }
    public Account saveAccount(Account account) { account.updatedAt = clock.instant(); return mongo.save(account); }
    public Optional<Account> findAccount(PublicId id) { return Optional.ofNullable(mongo.findById(id.toString(), Account.class)); }

    public Optional<Account> findByLogin(String normalizedLogin) {
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("normalizedEmail").is(normalizedLogin),
                Criteria.where("normalizedPhone").is(normalizedLogin));
        return Optional.ofNullable(mongo.findOne(Query.query(criteria), Account.class));
    }

    public Optional<Account> findByEmail(String email) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("normalizedEmail").is(email)), Account.class));
    }

    public Optional<Account> findByPhone(String phone) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("normalizedPhone").is(phone)), Account.class));
    }

    public Optional<Account> findByGoogleSubject(String subject) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("googleSubject").is(subject)), Account.class));
    }

    public List<Account> findByRole(String role) {
        return mongo.find(Query.query(Criteria.where("roles").is(role)), Account.class);
    }

    public DeviceSession createSession(PublicId principalId, DeviceInput device, boolean remembered,
                                       String approximateRegion) {
        Instant now = clock.instant();
        DeviceSession session = new DeviceSession();
        session.id = PublicId.generate().toString();
        session.principalId = principalId.toString();
        session.deviceId = device.deviceId();
        session.deviceName = device.deviceName();
        session.platform = device.platform();
        session.browserOrApp = device.browserOrApp();
        session.approximateRegion = approximateRegion;
        session.createdAt = now;
        session.lastSeenAt = now;
        session.remembered = remembered;
        session.riskStatus = "NORMAL";
        return mongo.insert(session);
    }

    public Optional<DeviceSession> findSession(PublicId id) {
        return Optional.ofNullable(mongo.findById(id.toString(), DeviceSession.class));
    }

    public List<DeviceSession> activeSessions(PublicId principalId) {
        Query query = Query.query(Criteria.where("principalId").is(principalId.toString()).and("revokedAt").is(null))
                .with(Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        return mongo.find(query, DeviceSession.class);
    }

    public void touchSession(DeviceSession session) { session.lastSeenAt = clock.instant(); mongo.save(session); }

    public void revokeSession(DeviceSession session, String reason) {
        if (session.revokedAt == null) {
            session.revokedAt = clock.instant();
            session.revocationReason = reason;
            mongo.save(session);
        }
    }

    public void recordActivity(Account account, DeviceSession session, String eventType, String outcome,
                               String reasonCode, String approximateRegion, String correlationId) {
        AuthActivity activity = new AuthActivity();
        activity.id = PublicId.generate().toString();
        activity.principalId = account == null ? null : account.id;
        activity.sessionId = session == null ? null : session.id;
        activity.eventType = eventType;
        activity.outcome = outcome;
        activity.reasonCode = reasonCode;
        activity.deviceSummary = session == null ? "Unknown device" : session.deviceName + " · " + session.browserOrApp;
        activity.approximateRegion = approximateRegion;
        activity.correlationId = correlationId;
        activity.occurredAt = clock.instant();
        mongo.insert(activity);
    }

    public List<AuthActivity> activity(PublicId principalId, int limit) {
        Query query = Query.query(Criteria.where("principalId").is(principalId.toString()))
                .with(Sort.by(Sort.Direction.DESC, "occurredAt")).limit(Math.min(Math.max(limit, 1), 100));
        return mongo.find(query, AuthActivity.class);
    }

    public record DeviceInput(String deviceId, String deviceName, String platform, String browserOrApp) {
        public DeviceInput {
            if (deviceId == null || !deviceId.matches("[A-Za-z0-9._:-]{8,128}")) throw new IllegalArgumentException("invalid deviceId");
            if (deviceName == null || deviceName.isBlank() || deviceName.length() > 80) throw new IllegalArgumentException("invalid deviceName");
            if (platform == null || !platform.matches("[A-Z][A-Z0-9_]{1,31}")) throw new IllegalArgumentException("invalid platform");
            if (browserOrApp == null || browserOrApp.isBlank() || browserOrApp.length() > 80) throw new IllegalArgumentException("invalid browserOrApp");
        }
    }

    @Document("identity_accounts")
    public static final class Account {
        @Id public String id;
        public String displayName;
        public String normalizedEmail;
        public String normalizedPhone;
        public String passwordHash;
        public String googleSubject;
        public boolean emailVerified;
        public boolean phoneVerified;
        public Set<String> roles = Set.of();
        public Set<String> permissions = Set.of();
        public String activeRole;
        public String status;
        public String locale;
        public String timeZone;
        public String acceptedTermsVersion;
        public boolean marketingConsent;
        public String businessDisplayName;
        public String primaryServiceZoneId;
        public String providerOnboardingStatus;
        public boolean mfaRequired;
        public String totpSecretBase32;
        public Instant passwordChangedAt;
        public Instant createdAt;
        public Instant updatedAt;
        @Version public Long version;

        public PublicId publicId() { return PublicId.parse(id); }
        public boolean isAdmin() { return roles.contains("ADMIN"); }
        public boolean isActive() { return "ACTIVE".equals(status); }
    }

    @Document("identity_sessions")
    public static final class DeviceSession {
        @Id public String id;
        public String principalId;
        public String deviceId;
        public String deviceName;
        public String platform;
        public String browserOrApp;
        public String approximateRegion;
        public Instant createdAt;
        public Instant lastSeenAt;
        public boolean remembered;
        public String riskStatus;
        public Instant revokedAt;
        public String revocationReason;

        public PublicId publicId() { return PublicId.parse(id); }
        public boolean active() { return revokedAt == null; }
    }

    @Document("authentication_activity")
    public static final class AuthActivity {
        @Id public String id;
        public String principalId;
        public String sessionId;
        public String eventType;
        public String outcome;
        public String reasonCode;
        public String deviceSummary;
        public String approximateRegion;
        public String correlationId;
        public Instant occurredAt;
    }
}
