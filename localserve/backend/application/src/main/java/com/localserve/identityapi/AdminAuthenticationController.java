package com.localserve.identityapi;

import com.localserve.shared.identity.PublicId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthenticationController {
    private final AuthenticationService authentication;
    private final CookieSecurityService cookies;

    public AdminAuthenticationController(AuthenticationService authentication, CookieSecurityService cookies) {
        this.authentication = authentication;
        this.cookies = cookies;
    }

    @PostMapping("/auth/password-sessions")
    ResponseEntity<?> passwordSession(@Valid @RequestBody AdminPasswordRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.LoginOutcome result = authentication.passwordLogin(body.email(), body.password(),
                false, body.device().input(), true, "ADMIN", context(request));
        if (result.requiresMfa()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                    .body(new MfaChallengeView(result.mfaChallenge().id().toString(), "TOTP",
                            result.mfaChallenge().expiresAt()));
        }
        return sessionResponse(result.session(), response);
    }

    @PostMapping("/auth/mfa-verifications")
    ResponseEntity<IdentityResponses.TokenView> verifyMfa(@Valid @RequestBody MfaVerificationRequest body,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.AuthenticatedSession session = authentication.verifyAdminMfa(
                PublicId.parse(body.challengeId()), body.code(), body.device().input(), context(request));
        return sessionResponse(session, response);
    }

    @PostMapping("/auth/token-refreshes")
    ResponseEntity<IdentityResponses.TokenView> refresh(HttpServletRequest request, HttpServletResponse response) {
        CookieSecurityService.PresentedRefresh presented = cookies.requireRefresh(request);
        AuthenticationService.AuthenticatedSession session = authentication.refresh(
                presented.sessionId(), presented.token(), context(request));
        if (!session.account().isAdmin()) {
            authentication.logout(presented.sessionId());
            cookies.clearSession(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return sessionResponse(session, response);
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        CookieSecurityService.PresentedRefresh presented = cookies.requireRefresh(request);
        authentication.logout(presented.sessionId());
        cookies.clearSession(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/auth/step-up-challenges")
    ResponseEntity<MfaChallengeView> requestStepUp(@AuthenticationPrincipal Jwt jwt) {
        AdminMfaService.Challenge challenge = authentication.requestAdminStepUp(AccountController.principal(jwt));
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(new MfaChallengeView(challenge.id().toString(), "TOTP", challenge.expiresAt()));
    }

    @PostMapping("/auth/step-up-verifications")
    ResponseEntity<StepUpView> verifyStepUp(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody StepUpVerificationRequest body) {
        JwtTokenService.IssuedAccessToken token = authentication.verifyAdminStepUp(
                PublicId.parse(body.challengeId()), body.code(), PublicId.parse(jwt.getClaimAsString("sid")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new StepUpView(token.token(), "Bearer", token.expiresInSeconds(), token.expiresAt()));
    }

    @GetMapping("/me")
    ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        IdentityPersistence.Account account = authentication.requireAccount(AccountController.principal(jwt));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "account", IdentityResponses.account(account),
                "sessionId", jwt.getClaimAsString("sid"),
                "mfaRequired", account.mfaRequired,
                "stepUpAt", jwt.getClaims().get("step_up_at") == null ? "" : jwt.getClaims().get("step_up_at")));
    }

    @GetMapping("/me/sessions")
    ResponseEntity<List<IdentityResponses.SessionView>> sessions(@AuthenticationPrincipal Jwt jwt) {
        String current = jwt.getClaimAsString("sid");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authentication
                .sessions(AccountController.principal(jwt)).stream()
                .map(session -> IdentityResponses.session(session, current)).toList());
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable String sessionId) {
        authentication.revokeOwnedSession(PublicId.parse(sessionId), AccountController.principal(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/me/session-revocations")
    ResponseEntity<Void> revokeOtherSessions(@AuthenticationPrincipal Jwt jwt) {
        PublicId principal = AccountController.principal(jwt);
        authentication.revokeOtherSessions(principal, PublicId.parse(jwt.getClaimAsString("sid")),
                "ADMIN_REVOKED_OTHER_SESSIONS");
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<IdentityResponses.TokenView> sessionResponse(
            AuthenticationService.AuthenticatedSession session, HttpServletResponse response) {
        cookies.writeSession(response, session.deviceSession().publicId(), session.refreshToken().token(),
                session.refreshToken().expiresAt());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(IdentityResponses.token(session));
    }

    private static AuthenticationService.RequestContext context(HttpServletRequest request) {
        String region = request.getHeader("X-Approximate-Region");
        if (region == null || !region.matches("[A-Za-z0-9 ,.()-]{2,80}")) region = "Unknown";
        return new AuthenticationService.RequestContext(region, MDC.get("correlationId"));
    }

    record AdminPasswordRequest(@NotBlank @Size(max = 254) String email,
                                @NotBlank @Size(max = 128) String password,
                                @Valid @NotNull PublicAuthenticationController.DeviceRequest device) { }
    record MfaVerificationRequest(@NotBlank String challengeId,
                                  @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
                                  @Valid @NotNull PublicAuthenticationController.DeviceRequest device) { }
    record StepUpVerificationRequest(@NotBlank String challengeId,
                                     @NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }
    record MfaChallengeView(String challengeId, String method, java.time.Instant expiresAt) { }
    record StepUpView(String accessToken, String tokenType, long expiresInSeconds,
                      java.time.Instant expiresAt) { }
}
