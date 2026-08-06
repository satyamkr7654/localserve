package com.localserve.identity.authorization;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record AuthorizationContext(
        PublicId principalId,
        PublicId sessionId,
        Role activeRole,
        Set<Role> memberships,
        Set<PermissionCode> permissions,
        Instant authenticatedAt,
        Instant stepUpAt) {
    public AuthorizationContext {
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(activeRole, "activeRole");
        memberships = Set.copyOf(Objects.requireNonNull(memberships, "memberships"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
        Objects.requireNonNull(authenticatedAt, "authenticatedAt");
        if (!memberships.contains(activeRole)) {
            throw new IllegalArgumentException("active role must be a membership");
        }
        if (activeRole == Role.ADMIN && memberships.size() != 1) {
            throw new IllegalArgumentException("admin identity cannot share public memberships");
        }
    }

    public void requireRole(Role role) {
        if (activeRole != role) {
            throw new DomainException("ACCESS.ROLE_CONTEXT_REQUIRED", "Required role context is not active");
        }
    }

    public void requirePermission(String permission) {
        if (!permissions.contains(new PermissionCode(permission))) {
            throw new DomainException("ACCESS.DENIED", "Required permission is missing");
        }
    }

    public void requireRecentStepUp(Instant now, java.time.Duration maximumAge) {
        if (stepUpAt == null || stepUpAt.plus(maximumAge).isBefore(now)) {
            throw new DomainException("AUTH.STEP_UP_REQUIRED", "Recent step-up authentication is required");
        }
    }
}
