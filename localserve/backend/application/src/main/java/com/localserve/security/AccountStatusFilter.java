package com.localserve.security;

import com.localserve.identityapi.IdentityPersistence;
import com.localserve.shared.identity.PublicId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AccountStatusFilter extends OncePerRequestFilter {
    private final IdentityPersistence persistence;
    private final SecurityProblemWriter problems;
    private final String publicAudience;
    private final String adminAudience;

    public AccountStatusFilter(IdentityPersistence persistence, SecurityProblemWriter problems,
                               @Value("${JWT_PUBLIC_AUDIENCE:localserve-public}") String publicAudience,
                               @Value("${JWT_ADMIN_AUDIENCE:localserve-admin}") String adminAudience) {
        this.persistence = persistence;
        this.problems = problems;
        this.publicAudience = publicAudience;
        this.adminAudience = adminAudience;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            var jwt = authentication.getToken();
            String sessionId = jwt.getClaimAsString("sid");
            String expectedAudience = request.getRequestURI().startsWith("/api/v1/admin/")
                    ? adminAudience : publicAudience;
            boolean active = false;
            try {
                IdentityPersistence.Account account = persistence.findAccount(PublicId.parse(jwt.getSubject())).orElse(null);
                IdentityPersistence.DeviceSession session = sessionId == null ? null
                        : persistence.findSession(PublicId.parse(sessionId)).orElse(null);
                active = jwt.getAudience().contains(expectedAudience) && account != null && account.isActive()
                        && session != null && session.active() && account.id.equals(session.principalId);
            } catch (IllegalArgumentException ignored) {
                active = false;
            }
            if (!active) {
                SecurityContextHolder.clearContext();
                problems.commence(request, response,
                        new InsufficientAuthenticationException("Account or session is unavailable"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
