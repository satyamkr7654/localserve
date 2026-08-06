package com.localserve.booking.domain;

public record CompletionEvidence(boolean requiredFilesAvailable, boolean consentPolicySatisfied) {
    public void requireSatisfied() {
        if (!requiredFilesAvailable || !consentPolicySatisfied) {
            throw new com.localserve.shared.error.DomainException(
                    "BOOKING.COMPLETION_EVIDENCE_REQUIRED",
                    "Required completion evidence is not available");
        }
    }
}
