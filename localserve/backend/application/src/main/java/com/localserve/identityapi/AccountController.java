package com.localserve.identityapi;

import com.localserve.shared.identity.PublicId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
    private final AuthenticationService authentication;

    public AccountController(AuthenticationService authentication) { this.authentication = authentication; }

    @GetMapping
    ResponseEntity<IdentityResponses.AccountView> account(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(IdentityResponses.account(authentication.requireAccount(principal(jwt))));
    }

    @GetMapping("/login-methods")
    ResponseEntity<List<LoginMethodView>> loginMethods(@AuthenticationPrincipal Jwt jwt) {
        IdentityPersistence.Account account = authentication.requireAccount(principal(jwt));
        List<LoginMethodView> methods = new java.util.ArrayList<>();
        if (account.passwordHash != null) methods.add(new LoginMethodView("password", "PASSWORD", "Password", true));
        if (account.normalizedEmail != null) methods.add(new LoginMethodView("email", "EMAIL", maskEmail(account.normalizedEmail), account.emailVerified));
        if (account.normalizedPhone != null) methods.add(new LoginMethodView("phone", "PHONE", maskPhone(account.normalizedPhone), account.phoneVerified));
        if (account.googleSubject != null) methods.add(new LoginMethodView("google", "GOOGLE", "Google", true));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(methods);
    }

    @GetMapping("/sessions")
    ResponseEntity<List<IdentityResponses.SessionView>> sessions(@AuthenticationPrincipal Jwt jwt) {
        String current = jwt.getClaimAsString("sid");
        List<IdentityResponses.SessionView> sessions = authentication.sessions(principal(jwt)).stream()
                .map(session -> IdentityResponses.session(session, current)).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable String sessionId) {
        authentication.revokeOwnedSession(PublicId.parse(sessionId), principal(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/session-revocations")
    ResponseEntity<Map<String, Object>> revokeSessions(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody SessionRevocationRequest body) {
        PublicId principal = principal(jwt);
        PublicId current = PublicId.parse(jwt.getClaimAsString("sid"));
        if (body.scope() == RevocationScope.ALL) authentication.revokeAll(principal, "USER_REVOKED_ALL");
        else authentication.revokeOtherSessions(principal, current, "USER_REVOKED_OTHER_SESSIONS");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("scope", body.scope(), "revoked", true));
    }

    @GetMapping("/auth-activity")
    ResponseEntity<List<ActivityView>> activity(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "25") int limit) {
        List<ActivityView> activity = authentication.activity(principal(jwt), limit).stream()
                .map(item -> new ActivityView(item.id, item.eventType, item.outcome, item.reasonCode,
                        item.deviceSummary, item.approximateRegion, item.occurredAt)).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activity);
    }

    static PublicId principal(Jwt jwt) { return PublicId.parse(jwt.getSubject()); }

    private static String maskEmail(String email) {
        int separator = email.indexOf('@');
        return email.substring(0, Math.min(2, separator)) + "***" + email.substring(separator);
    }

    private static String maskPhone(String phone) {
        return "***" + phone.substring(Math.max(0, phone.length() - 4));
    }

    enum RevocationScope { ALL_OTHER, ALL }
    record SessionRevocationRequest(@NotNull RevocationScope scope) { }
    record LoginMethodView(String id, String type, String label, boolean verified) { }
    record ActivityView(String id, String eventType, String outcome, String reasonCode,
                        String deviceSummary, String approximateRegion, java.time.Instant occurredAt) { }
}
