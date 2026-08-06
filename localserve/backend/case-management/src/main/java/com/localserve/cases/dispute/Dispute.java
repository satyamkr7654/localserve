package com.localserve.cases.dispute;

import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Dispute {
    private final PublicId id = PublicId.generate();
    private final PublicId bookingId;
    private final PublicId raisedBy;
    private final Money heldAmount;
    private final Instant openedAt;
    private final List<PublicId> evidenceFileIds = new ArrayList<>();
    private DisputeStatus status = DisputeStatus.OPEN;
    private DisputeResolution resolution;
    private PublicId decidedByAdminId;
    private long version;

    public Dispute(PublicId bookingId, PublicId raisedBy, Money heldAmount, Clock clock) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.raisedBy = Objects.requireNonNull(raisedBy, "raisedBy");
        this.heldAmount = Objects.requireNonNull(heldAmount, "heldAmount").requirePositive("heldAmount");
        this.openedAt = Objects.requireNonNull(clock, "clock").instant();
    }

    public void addEvidence(PublicId fileId) {
        if (isResolved()) throw closed();
        if (evidenceFileIds.size() >= 20) throw new DomainException("DISPUTE.EVIDENCE_LIMIT", "Evidence item limit reached");
        if (!evidenceFileIds.contains(fileId)) evidenceFileIds.add(Objects.requireNonNull(fileId, "fileId"));
        status = DisputeStatus.EVIDENCE_COLLECTION;
        version++;
    }

    public void beginReview(PublicId adminId) {
        if (isResolved()) throw closed();
        Objects.requireNonNull(adminId, "adminId");
        status = DisputeStatus.UNDER_REVIEW;
        version++;
    }

    public void resolve(PublicId adminId, DisputeResolution decision) {
        if (status != DisputeStatus.UNDER_REVIEW) throw new DomainException("DISPUTE.INVALID_STATE", "Dispute must be under review");
        Objects.requireNonNull(adminId, "adminId");
        Objects.requireNonNull(decision, "decision");
        if (!heldAmount.equals(decision.refundAmount().add(decision.releaseAmount()))) {
            throw new DomainException("DISPUTE.ALLOCATION_MISMATCH", "Resolution allocation must equal held amount");
        }
        this.resolution = decision;
        this.decidedByAdminId = adminId;
        this.status = decision.refundAmount().amountMinor() == 0 ? DisputeStatus.RESOLVED_RELEASE
                : decision.releaseAmount().amountMinor() == 0 ? DisputeStatus.RESOLVED_REFUND
                : DisputeStatus.RESOLVED_PARTIAL_REFUND;
        version++;
    }

    private boolean isResolved() { return status.name().startsWith("RESOLVED_"); }
    private static DomainException closed() { return new DomainException("DISPUTE.ALREADY_RESOLVED", "Dispute is already resolved"); }
    public PublicId id() { return id; }
    public DisputeStatus status() { return status; }
    public List<PublicId> evidenceFileIds() { return List.copyOf(evidenceFileIds); }
}
