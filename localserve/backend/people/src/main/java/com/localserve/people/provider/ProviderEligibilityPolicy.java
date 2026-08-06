package com.localserve.people.provider;

import com.localserve.shared.identity.PublicId;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProviderEligibilityPolicy {
    private final Clock clock;
    private final Duration locationFreshness;
    private final int maximumConcurrentBookings;

    public ProviderEligibilityPolicy(Clock clock, Duration locationFreshness, int maximumConcurrentBookings) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.locationFreshness = Objects.requireNonNull(locationFreshness, "locationFreshness");
        if (locationFreshness.isNegative() || locationFreshness.isZero() || maximumConcurrentBookings < 1) {
            throw new IllegalArgumentException("provider eligibility policy is invalid");
        }
        this.maximumConcurrentBookings = maximumConcurrentBookings;
    }

    public ProviderEligibilityDecision evaluate(ProviderEligibilitySnapshot provider, PublicId serviceId) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(serviceId, "serviceId");
        List<String> reasons = new ArrayList<>();
        if (provider.verificationStatus() != ProviderVerificationStatus.APPROVED) reasons.add("PROVIDER_NOT_APPROVED");
        if (!provider.accountActive()) reasons.add("ACCOUNT_INACTIVE");
        if (!provider.online() || !provider.acceptingWork()) reasons.add("NOT_ACCEPTING_WORK");
        if (!provider.approvedServiceIds().contains(serviceId)) reasons.add("SERVICE_NOT_APPROVED");
        if (provider.lastLocationAt().isBefore(clock.instant().minus(locationFreshness))) reasons.add("LOCATION_STALE");
        if (provider.activeBookingIds().size() >= maximumConcurrentBookings) reasons.add("CAPACITY_REACHED");
        return new ProviderEligibilityDecision(reasons.isEmpty(), reasons);
    }
}
