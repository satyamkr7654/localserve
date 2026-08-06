package com.localserve.people.provider;

import java.util.List;

public record ProviderEligibilityDecision(boolean eligible, List<String> reasonCodes) {
    public ProviderEligibilityDecision {
        reasonCodes = List.copyOf(reasonCodes);
        if (eligible == !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("eligibility and reasons are inconsistent");
        }
    }
}
