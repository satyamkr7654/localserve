package com.localserve.identityapi;

import com.localserve.identity.otp.OtpPurpose;
import com.localserve.shared.identity.PublicId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class PublicAuthenticationController {
    private final AuthenticationService authentication;
    private final CookieSecurityService cookies;
    private final JwtKeyConfiguration.RsaKeyMaterial keys;

    public PublicAuthenticationController(AuthenticationService authentication,
                                          CookieSecurityService cookies,
                                          JwtKeyConfiguration.RsaKeyMaterial keys) {
        this.authentication = authentication;
        this.cookies = cookies;
        this.keys = keys;
    }

    @PostMapping("/customer-registrations")
    ResponseEntity<IdentityResponses.RegistrationView> registerCustomer(
            @Valid @RequestBody RegistrationRequest body, HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.Registration result = authentication.registerCustomer(body.command(), context(request));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(registration(result));
    }

    @PostMapping("/provider-registrations")
    ResponseEntity<IdentityResponses.RegistrationView> registerProvider(
            @Valid @RequestBody ProviderRegistrationRequest body, HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.Registration result = authentication.registerProvider(body.command(), context(request));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(registration(result));
    }

    @PostMapping("/password-sessions")
    ResponseEntity<?> passwordSession(@Valid @RequestBody PasswordSessionRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.LoginOutcome result = authentication.passwordLogin(body.login(), body.password(),
                body.rememberMe(), body.device().input(), false, body.requiredRole(), context(request));
        if (result.requiresMfa()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                    .body(new MfaChallengeView(result.mfaChallenge().id().toString(), "TOTP",
                            result.mfaChallenge().expiresAt()));
        }
        return sessionResponse(result.session(), response);
    }

    @PostMapping("/mfa-verifications")
    ResponseEntity<IdentityResponses.TokenView> verifyMfa(@Valid @RequestBody MfaVerificationRequest body,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.AuthenticatedSession session = authentication.verifyMfa(
                PublicId.parse(body.challengeId()), body.code(), body.device().input(),
                body.requiredRole(), context(request));
        return sessionResponse(session, response);
    }

    @PostMapping("/phone-otp-challenges")
    ResponseEntity<OtpChallengeView> issuePhoneOtp(@Valid @RequestBody PhoneOtpChallengeRequest body,
                                                   HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.OtpIssue issued = authentication.issuePhoneOtp(body.phone(), body.purpose());
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(new OtpChallengeView(issued.challengeId().toString(), issued.expiresAt(), issued.issuanceVersion()));
    }

    @PostMapping("/phone-otp-verifications")
    ResponseEntity<?> verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerificationRequest body,
                                     HttpServletRequest request, HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        AuthenticationService.PhoneOtpResult result = authentication.verifyPhoneOtp(
                PublicId.parse(body.challengeId()), body.phone(), body.purpose(), body.code(),
                body.issuanceVersion(), body.rememberMe(), body.device().input(), context(request));
        if (result.session() == null) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("verified", true, "account", IdentityResponses.account(result.account())));
        }
        return sessionResponse(result.session(), response);
    }

    @PostMapping("/email-verification-challenges")
    ResponseEntity<Void> requestEmailVerification(@Valid @RequestBody EmailRequest body,
                                                  HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        authentication.requestEmailVerification(body.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/email-verifications")
    ResponseEntity<Map<String, Object>> verifyEmail(@Valid @RequestBody TokenRequest body,
                                                    HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        IdentityPersistence.Account account = authentication.verifyEmail(body.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("verified", true, "account", IdentityResponses.account(account)));
    }

    @PostMapping("/password-recovery-requests")
    ResponseEntity<Void> requestPasswordRecovery(@Valid @RequestBody EmailRequest body,
                                                 HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        authentication.requestPasswordRecovery(body.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/password-resets")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest body,
                                       HttpServletRequest request) {
        cookies.requireAllowedOrigin(request, true);
        authentication.resetPassword(body.token(), body.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/token-refreshes")
    ResponseEntity<IdentityResponses.TokenView> refresh(HttpServletRequest request,
                                                        HttpServletResponse response) {
        CookieSecurityService.PresentedRefresh presented = cookies.requireRefresh(request);
        return sessionResponse(authentication.refresh(presented.sessionId(), presented.token(), context(request)), response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        CookieSecurityService.PresentedRefresh presented = cookies.requireRefresh(request);
        authentication.logout(presented.sessionId());
        cookies.clearSession(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/jwks")
    Map<String, Object> jwks() { return keys.publicJwkSet(); }

    private ResponseEntity<IdentityResponses.TokenView> sessionResponse(
            AuthenticationService.AuthenticatedSession session, HttpServletResponse response) {
        cookies.writeSession(response, session.deviceSession().publicId(), session.refreshToken().token(),
                session.refreshToken().expiresAt());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(IdentityResponses.token(session));
    }

    private static IdentityResponses.RegistrationView registration(AuthenticationService.Registration result) {
        List<String> nextSteps = result.account().emailVerified ? List.of() : List.of("VERIFY_EMAIL");
        return new IdentityResponses.RegistrationView(IdentityResponses.account(result.account()), nextSteps,
                result.verificationDelivered());
    }

    private static AuthenticationService.RequestContext context(HttpServletRequest request) {
        String region = request.getHeader("X-Approximate-Region");
        if (region == null || !region.matches("[A-Za-z0-9 ,.()-]{2,80}")) region = "Unknown";
        return new AuthenticationService.RequestContext(region, MDC.get("correlationId"));
    }

    record DeviceRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String deviceId,
                         @NotBlank @Size(max = 80) String deviceName,
                         @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String platform,
                         @NotBlank @Size(max = 80) String browserOrApp) {
        IdentityPersistence.DeviceInput input() {
            return new IdentityPersistence.DeviceInput(deviceId, deviceName, platform, browserOrApp);
        }
    }

    record RegistrationRequest(@NotBlank @Size(min = 2, max = 80) String displayName,
                               @NotBlank @Email @Size(max = 254) String email,
                               @Pattern(regexp = "^$|^\\+[1-9][0-9]{7,14}$") String phone,
                               @NotBlank @Size(min = 12, max = 128) String password,
                               @NotBlank @Size(max = 20) String locale,
                               @NotBlank @Size(max = 64) String timeZone,
                               @NotBlank @Size(max = 40) String acceptedTermsVersion,
                               boolean marketingConsent) {
        AuthenticationService.RegistrationCommand command() {
            return new AuthenticationService.RegistrationCommand(displayName, email, phone, password, locale,
                    timeZone, acceptedTermsVersion, marketingConsent, null, null);
        }
    }

    record ProviderRegistrationRequest(@NotBlank @Size(min = 2, max = 80) String displayName,
                                       @NotBlank @Email @Size(max = 254) String email,
                                       @Pattern(regexp = "^$|^\\+[1-9][0-9]{7,14}$") String phone,
                                       @NotBlank @Size(min = 12, max = 128) String password,
                                       @NotBlank @Size(max = 20) String locale,
                                       @NotBlank @Size(max = 64) String timeZone,
                                       @NotBlank @Size(max = 40) String acceptedTermsVersion,
                                       boolean marketingConsent,
                                       @NotBlank @Size(max = 120) String businessDisplayName,
                                       @NotBlank @Size(max = 80) String primaryServiceZoneId) {
        AuthenticationService.RegistrationCommand command() {
            return new AuthenticationService.RegistrationCommand(displayName, email, phone, password, locale,
                    timeZone, acceptedTermsVersion, marketingConsent, businessDisplayName, primaryServiceZoneId);
        }
    }

    record PasswordSessionRequest(@NotBlank @Size(max = 254) String login,
                                  @NotBlank @Size(max = 128) String password,
                                  boolean rememberMe,
                                  @NotBlank @Pattern(regexp = "CUSTOMER|PROVIDER") String requiredRole,
                                  @Valid @NotNull DeviceRequest device) { }
    record MfaVerificationRequest(@NotBlank String challengeId,
                                  @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
                                  @NotBlank @Pattern(regexp = "CUSTOMER|PROVIDER") String requiredRole,
                                  @Valid @NotNull DeviceRequest device) { }
    record PhoneOtpChallengeRequest(@NotBlank String phone, @NotNull OtpPurpose purpose) { }
    record PhoneOtpVerificationRequest(@NotBlank String challengeId, @NotBlank String phone,
                                       @NotNull OtpPurpose purpose,
                                       @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
                                       long issuanceVersion, boolean rememberMe,
                                       @Valid @NotNull DeviceRequest device) { }
    record EmailRequest(@NotBlank @Email @Size(max = 254) String email) { }
    record TokenRequest(@NotBlank @Size(min = 40, max = 128) String token) { }
    record PasswordResetRequest(@NotBlank @Size(min = 40, max = 128) String token,
                                @NotBlank @Size(min = 12, max = 128) String newPassword) { }
    record OtpChallengeView(String challengeId, java.time.Instant expiresAt, long issuanceVersion) { }
    record MfaChallengeView(String challengeId, String method, java.time.Instant expiresAt) { }
}
