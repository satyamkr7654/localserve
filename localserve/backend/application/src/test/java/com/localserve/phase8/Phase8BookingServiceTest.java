package com.localserve.phase8;

import com.localserve.booking.application.BookingPersistenceService;
import com.localserve.booking.application.BookingRepository;
import com.localserve.identity.otp.OtpService;
import com.localserve.identityapi.AuthDeliveryService;
import com.localserve.identityapi.IdentityPersistence;
import com.localserve.shared.identity.PublicId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Phase8BookingServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    private Phase8Persistence persistence;
    private IdentityPersistence identities;
    private Phase8BookingService service;
    private PublicId customerId;

    @BeforeEach
    void setUp() {
        persistence = mock(Phase8Persistence.class);
        identities = mock(IdentityPersistence.class);
        customerId = PublicId.generate();
        IdentityPersistence.Account customer = new IdentityPersistence.Account();
        customer.id = customerId.toString();
        customer.roles = Set.of("CUSTOMER");
        customer.status = "ACTIVE";
        when(identities.findAccount(customerId)).thenReturn(Optional.of(customer));
        when(persistence.insertQuote(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new Phase8BookingService(persistence, identities,
                mock(BookingRepository.class), mock(BookingPersistenceService.class),
                mock(OtpService.class), mock(AuthDeliveryService.class),
                Clock.fixed(NOW, ZoneOffset.UTC), "test", "a".repeat(64));
    }

    @Test
    void emergencyQuoteUsesServerCatalogAndExpiresInFifteenMinutes() {
        Phase8BookingService.QuoteView quote = service.createQuote(customerId,
                new Phase8BookingService.QuoteCommand("electrician", "EMERGENCY", "Noida-Central"));

        assertThat(quote.serviceCode()).isEqualTo("electrician");
        assertThat(quote.amountMinor()).isEqualTo(119_850L);
        assertThat(quote.currency()).isEqualTo("INR");
        assertThat(quote.serviceZoneId()).isEqualTo("noida-central");
        assertThat(quote.expiresAt()).isEqualTo(NOW.plusSeconds(15 * 60));
    }

    @Test
    void catalogExposesStablePhase8ServiceCodes() {
        assertThat(service.catalog()).extracting(Phase8BookingService.ServiceView::code)
                .containsExactly("electrician", "plumber", "ac-repair", "cleaning",
                        "painter", "mechanic", "laptop", "mobile");
    }
}
