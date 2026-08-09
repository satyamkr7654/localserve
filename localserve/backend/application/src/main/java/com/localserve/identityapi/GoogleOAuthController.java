package com.localserve.identityapi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/oauth/google")
public class GoogleOAuthController {
    private final GoogleOAuthService google;
    private final AuthenticationService authentication;
    private final CookieSecurityService cookies;

    public GoogleOAuthController(GoogleOAuthService google, AuthenticationService authentication,
                                 CookieSecurityService cookies) {
        this.google = google;
        this.authentication = authentication;
        this.cookies = cookies;
    }

    @PostMapping("/authorization-requests")
    ResponseEntity<Map<String, Object>> begin(@Valid @RequestBody AuthorizationBody body,
                                              HttpServletRequest request, HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        GoogleOAuthService.AuthorizationRequest result = google.begin(body.role());
        cookies.writeOAuthState(response, result.state());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "authorizationUrl", result.authorizationUrl(), "expiresAt", result.expiresAt()));
    }

    @GetMapping("/callback")
    ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state,
                                  HttpServletRequest request, HttpServletResponse response) {
        cookies.requireOAuthState(request, response, state);
        GoogleOAuthService.CallbackResult result = google.complete(code, state);
        cookies.writeOAuthResult(response, result.resultToken());
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, result.redirectUrl()).cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/result-exchanges")
    ResponseEntity<?> exchange(@Valid @RequestBody ExchangeBody body,
                               HttpServletRequest request, HttpServletResponse response) {
        cookies.requireAllowedOrigin(request, true);
        GoogleOAuthService.ExchangeResult result = google.exchange(cookies.consumeOAuthResult(request, response));
        if ("LINK_REQUIRED".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(Map.of(
                    "code", "AUTH.OAUTH_LINK_REQUIRED",
                    "detail", "Sign in with the existing account before linking Google"));
        }
        AuthenticationService.AuthenticatedSession session = authentication.startOAuthSession(
                result.accountId(), body.device().input(), context(request));
        cookies.writeSession(response, session.deviceSession().publicId(), session.refreshToken().token(),
                session.refreshToken().expiresAt());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(IdentityResponses.token(session));
    }

    private static AuthenticationService.RequestContext context(HttpServletRequest request) {
        String region = request.getHeader("X-Approximate-Region");
        if (region == null || !region.matches("[A-Za-z0-9 ,.()-]{2,80}")) region = "Unknown";
        return new AuthenticationService.RequestContext(region, MDC.get("correlationId"));
    }

    record AuthorizationBody(@NotBlank @Pattern(regexp = "CUSTOMER|PROVIDER") String role) { }
    record ExchangeBody(@Valid @NotNull PublicAuthenticationController.DeviceRequest device) { }
}
