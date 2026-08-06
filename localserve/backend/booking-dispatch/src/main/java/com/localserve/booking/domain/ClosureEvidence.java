package com.localserve.booking.domain;

public record ClosureEvidence(
        boolean noOpenDispute,
        boolean financeComplete,
        boolean invoiceOutcomeRecorded,
        boolean ledgerReconciled) {
    public void requireSatisfied() {
        if (!noOpenDispute || !financeComplete || !invoiceOutcomeRecorded || !ledgerReconciled) {
            throw new com.localserve.shared.error.DomainException(
                    "BOOKING.CLOSURE_CONDITIONS_NOT_MET",
                    "Booking closure conditions are not met");
        }
    }
}
