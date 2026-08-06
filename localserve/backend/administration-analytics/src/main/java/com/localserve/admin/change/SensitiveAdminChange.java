package com.localserve.admin.change;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Maker-checker aggregate for high-risk platform configuration changes. */
public final class SensitiveAdminChange {
    private final PublicId id = PublicId.generate();
    private final PublicId proposedBy;
    private final String changeType;
    private final Map<String, String> redactedChangeSet;
    private final Instant expiresAt;
    private AdminChangeStatus status = AdminChangeStatus.PROPOSED;
    private PublicId approvedBy;

    public SensitiveAdminChange(PublicId proposedBy, String changeType, Map<String, String> redactedChangeSet,
                                Clock clock, Duration approvalTtl) {
        this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy");
        if (changeType == null || !changeType.matches("[A-Z][A-Z0-9_]{2,79}")) throw new IllegalArgumentException("invalid changeType");
        this.changeType = changeType;
        this.redactedChangeSet = Map.copyOf(redactedChangeSet);
        if (this.redactedChangeSet.isEmpty()) throw new IllegalArgumentException("change set must not be empty");
        this.expiresAt = clock.instant().plus(approvalTtl);
    }

    public void approve(PublicId checkerId, Clock clock) {
        Objects.requireNonNull(checkerId, "checkerId");
        if (status != AdminChangeStatus.PROPOSED) throw invalidState();
        if (proposedBy.equals(checkerId)) throw new DomainException("ADMIN.SELF_APPROVAL_DENIED", "A different administrator must approve this change");
        if (clock.instant().isAfter(expiresAt)) { status = AdminChangeStatus.EXPIRED; throw invalidState(); }
        approvedBy = checkerId;
        status = AdminChangeStatus.APPROVED;
    }

    public void execute(PublicId executorId) {
        Objects.requireNonNull(executorId, "executorId");
        if (status != AdminChangeStatus.APPROVED) throw invalidState();
        status = AdminChangeStatus.EXECUTED;
    }

    private static DomainException invalidState() { return new DomainException("ADMIN.CHANGE_INVALID_STATE", "Sensitive change is not in the required state"); }
    public AdminChangeStatus status() { return status; }
    public PublicId approvedBy() { return approvedBy; }
}
