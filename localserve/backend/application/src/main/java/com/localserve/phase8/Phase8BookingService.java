package com.localserve.phase8;

import com.localserve.booking.application.BookingPersistenceService;
import com.localserve.booking.application.BookingRepository;
import com.localserve.booking.domain.Booking;
import com.localserve.booking.domain.BookingStatus;
import com.localserve.booking.domain.BookingType;
import com.localserve.booking.domain.CompletionEvidence;
import com.localserve.booking.domain.VerifiedOtpEvidence;
import com.localserve.booking.domain.VerifiedPaymentEvidence;
import com.localserve.identity.otp.OtpDelivery;
import com.localserve.identity.otp.OtpService;
import com.localserve.identityapi.AuthDeliveryService;
import com.localserve.identityapi.IdentityPersistence;
import com.localserve.shared.error.DomainException;
import com.localserve.shared.identity.PublicId;
import com.localserve.shared.money.Money;
import com.localserve.shared.security.Actor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class Phase8BookingService {
    private static final Duration QUOTE_TTL = Duration.ofMinutes(15);
    private static final Duration OFFER_TTL = Duration.ofMinutes(30);
    private static final List<ServiceDefinition> CATALOG = List.of(
            new ServiceDefinition("019a0000-0000-7000-8000-000000000001", "electrician", "Electrician", 79900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000002", "plumber", "Plumber", 69900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000003", "ac-repair", "AC repair", 99900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000004", "cleaning", "Cleaning", 119900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000005", "painter", "Painter", 149900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000006", "mechanic", "Mechanic", 89900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000007", "laptop", "Laptop repair", 79900),
            new ServiceDefinition("019a0000-0000-7000-8000-000000000008", "mobile", "Mobile repair", 59900));

    private final Phase8Persistence persistence;
    private final IdentityPersistence identities;
    private final BookingRepository bookings;
    private final BookingPersistenceService bookingPersistence;
    private final OtpService otps;
    private final AuthDeliveryService delivery;
    private final Clock clock;
    private final String environment;
    private final String subjectPepper;

    public Phase8BookingService(Phase8Persistence persistence, IdentityPersistence identities,
                                BookingRepository bookings, BookingPersistenceService bookingPersistence,
                                OtpService otps, AuthDeliveryService delivery, Clock clock,
                                @Value("${APP_ENVIRONMENT:local}") String environment,
                                @Value("${OTP_HMAC_PEPPER}") String subjectPepper) {
        this.persistence = persistence;
        this.identities = identities;
        this.bookings = bookings;
        this.bookingPersistence = bookingPersistence;
        this.otps = otps;
        this.delivery = delivery;
        this.clock = clock;
        this.environment = environment;
        this.subjectPepper = subjectPepper;
    }

    @Transactional(readOnly = true)
    public List<ServiceView> catalog() {
        return CATALOG.stream().map(item -> new ServiceView(
                item.id(), item.code(), item.name(), item.baseAmountMinor(), "INR")).toList();
    }

    public ProviderView saveOnboarding(PublicId providerId, ProviderCommand command) {
        IdentityPersistence.Account account = requireRole(providerId, "PROVIDER");
        if (command.serviceCodes() == null) {
            throw new DomainException("PROVIDER.SKILL_REQUIRED", "Select at least one supported service");
        }
        Set<String> serviceCodes = command.serviceCodes().stream()
                .map(this::normalizeServiceCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (serviceCodes.isEmpty()) {
            throw new DomainException("PROVIDER.SKILL_REQUIRED", "Select at least one supported service");
        }
        requireText(command.businessDisplayName(), 2, 120, "businessDisplayName");
        requireText(command.serviceZoneId(), 2, 80, "serviceZoneId");

        Phase8Persistence.ProviderProfile profile = persistence.provider(providerId.toString())
                .orElseGet(Phase8Persistence.ProviderProfile::new);
        profile.providerId = providerId.toString();
        profile.businessDisplayName = command.businessDisplayName().trim();
        profile.serviceZoneId = command.serviceZoneId().trim().toLowerCase(Locale.ROOT);
        profile.serviceCodes = serviceCodes;
        profile.capacity = Math.min(Math.max(command.capacity(), 1), 5);
        profile.online = false;
        profile.onboardingStatus = "SUBMITTED";
        profile.updatedAt = clock.instant();
        profile = persistence.saveProvider(profile);

        account.businessDisplayName = profile.businessDisplayName;
        account.primaryServiceZoneId = profile.serviceZoneId;
        account.providerOnboardingStatus = "SUBMITTED";
        identities.saveAccount(account);
        return providerView(account, profile);
    }

    @Transactional(readOnly = true)
    public ProviderView providerOnboarding(PublicId providerId) {
        IdentityPersistence.Account account = requireRole(providerId, "PROVIDER");
        Phase8Persistence.ProviderProfile profile = persistence.provider(providerId.toString())
                .orElseGet(() -> draftProfile(account));
        return providerView(account, profile);
    }

    public ProviderView approveProvider(PublicId adminId, PublicId providerId, boolean approved, String reason) {
        requireRole(adminId, "ADMIN");
        requireText(reason, 3, 500, "reason");
        IdentityPersistence.Account account = requireRole(providerId, "PROVIDER");
        Phase8Persistence.ProviderProfile profile = persistence.provider(providerId.toString())
                .orElseThrow(() -> new DomainException("PROVIDER.ONBOARDING_REQUIRED",
                        "Provider onboarding must be submitted before review"));
        String decision = approved ? "APPROVED" : "REJECTED";
        profile.onboardingStatus = decision;
        profile.online = false;
        profile.updatedAt = clock.instant();
        profile = persistence.saveProvider(profile);
        account.providerOnboardingStatus = decision;
        identities.saveAccount(account);
        return providerView(account, profile);
    }

    @Transactional(readOnly = true)
    public List<ProviderView> verificationQueue(PublicId adminId) {
        requireRole(adminId, "ADMIN");
        return identities.findByRole("PROVIDER").stream()
                .map(account -> providerView(account, persistence.provider(account.id)
                        .orElseGet(() -> draftProfile(account))))
                .toList();
    }

    public ProviderView setOnline(PublicId providerId, boolean online) {
        IdentityPersistence.Account account = requireRole(providerId, "PROVIDER");
        Phase8Persistence.ProviderProfile profile = persistence.provider(providerId.toString())
                .orElseThrow(() -> new DomainException("PROVIDER.ONBOARDING_REQUIRED",
                        "Complete provider onboarding before changing availability"));
        if (online && !"APPROVED".equals(profile.onboardingStatus)) {
            throw new DomainException("PROVIDER.NOT_APPROVED", "Provider approval is required before going online");
        }
        if (online && profile.serviceCodes.isEmpty()) {
            throw new DomainException("PROVIDER.SKILL_REQUIRED", "At least one approved service is required");
        }
        profile.online = online;
        profile.updatedAt = clock.instant();
        return providerView(account, persistence.saveProvider(profile));
    }

    public QuoteView createQuote(PublicId customerId, QuoteCommand command) {
        requireRole(customerId, "CUSTOMER");
        ServiceDefinition service = service(command.serviceCode());
        BookingType bookingType = parseBookingType(command.bookingType());
        requireText(command.serviceZoneId(), 2, 80, "serviceZoneId");
        long amount = bookingType == BookingType.EMERGENCY
                ? Math.multiplyExact(service.baseAmountMinor(), 150) / 100
                : service.baseAmountMinor();
        Instant now = clock.instant();
        Phase8Persistence.Quote quote = new Phase8Persistence.Quote();
        quote.id = PublicId.generate().toString();
        quote.customerId = customerId.toString();
        quote.serviceId = service.id();
        quote.serviceCode = service.code();
        quote.serviceName = service.name();
        quote.bookingType = bookingType.name();
        quote.serviceZoneId = command.serviceZoneId().trim().toLowerCase(Locale.ROOT);
        quote.amountMinor = amount;
        quote.currency = "INR";
        quote.createdAt = now;
        quote.expiresAt = now.plus(QUOTE_TTL);
        quote = persistence.insertQuote(quote);
        return quoteView(quote);
    }

    public BookingView createBooking(PublicId customerId, CreateBookingCommand command) {
        requireRole(customerId, "CUSTOMER");
        Phase8Persistence.Quote quote = persistence.quote(command.quoteId())
                .orElseThrow(() -> new DomainException("PAYMENT.QUOTE_EXPIRED", "Booking quote is unavailable"));
        if (!quote.customerId.equals(customerId.toString()) || !quote.expiresAt.isAfter(clock.instant())) {
            throw new DomainException("PAYMENT.QUOTE_EXPIRED", "Booking quote has expired");
        }
        if (quote.consumed) {
            if (quote.bookingId != null) return customerBooking(customerId, PublicId.parse(quote.bookingId));
            throw new DomainException("BOOKING.QUOTE_ALREADY_USED", "Booking quote has already been used");
        }
        requireText(command.address(), 5, 500, "address");
        requireText(command.problemDescription(), 5, 2000, "problemDescription");

        Booking booking = Booking.create(customerId, PublicId.parse(quote.serviceId),
                BookingType.valueOf(quote.bookingType), Money.of(quote.amountMinor, quote.currency), clock);
        booking.beginProviderSearch(Actor.system(), PublicId.generate());
        List<Phase8Persistence.ProviderProfile> providers =
                persistence.eligibleProviders(quote.serviceCode, quote.serviceZoneId);
        if (!providers.isEmpty()) booking.recordProvidersFound(Actor.system(), PublicId.generate());
        booking = bookingPersistence.save(booking);

        Instant now = clock.instant();
        Phase8Persistence.BookingView view = new Phase8Persistence.BookingView();
        view.id = booking.id().toString();
        view.customerId = customerId.toString();
        view.serviceId = quote.serviceId;
        view.serviceCode = quote.serviceCode;
        view.serviceName = quote.serviceName;
        view.bookingType = quote.bookingType;
        view.serviceZoneId = quote.serviceZoneId;
        view.address = command.address().trim();
        view.problemDescription = command.problemDescription().trim();
        view.expectedAmountMinor = quote.amountMinor;
        view.currency = quote.currency;
        view.createdAt = now;
        view.updatedAt = now;
        sync(view, booking);
        persistence.insertBookingView(view);

        for (Phase8Persistence.ProviderProfile provider : providers) {
            Phase8Persistence.Offer offer = new Phase8Persistence.Offer();
            offer.id = PublicId.generate().toString();
            offer.bookingId = booking.id().toString();
            offer.providerId = provider.providerId;
            offer.status = "PENDING";
            offer.createdAt = now;
            offer.updatedAt = now;
            offer.expiresAt = now.plus(OFFER_TTL);
            persistence.insertOffer(offer);
        }
        quote.consumed = true;
        quote.bookingId = booking.id().toString();
        persistence.saveQuote(quote);
        return bookingView(view);
    }

    @Transactional(readOnly = true)
    public List<BookingView> customerBookings(PublicId customerId) {
        requireRole(customerId, "CUSTOMER");
        return persistence.customerBookings(customerId.toString()).stream().map(this::bookingView).toList();
    }

    @Transactional(readOnly = true)
    public BookingView customerBooking(PublicId customerId, PublicId bookingId) {
        requireRole(customerId, "CUSTOMER");
        return bookingView(requireOwnedView(customerId, bookingId));
    }

    @Transactional(readOnly = true)
    public List<OfferView> customerOffers(PublicId customerId, PublicId bookingId) {
        requireOwnedView(customerId, bookingId);
        return persistence.bookingOffers(bookingId.toString()).stream()
                .filter(offer -> Set.of("ACCEPTED_BY_PROVIDER", "SELECTED_BY_CUSTOMER", "NOT_SELECTED")
                        .contains(offer.status))
                .map(this::offerView).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferView> providerOffers(PublicId providerId) {
        requireRole(providerId, "PROVIDER");
        return persistence.providerOffers(providerId.toString()).stream().map(this::offerView).toList();
    }

    public OfferView acceptOffer(PublicId providerId, PublicId offerId, OfferDecision command) {
        requireRole(providerId, "PROVIDER");
        Phase8Persistence.ProviderProfile profile = persistence.provider(providerId.toString())
                .orElseThrow(() -> new DomainException("PROVIDER.ONBOARDING_REQUIRED", "Provider profile is unavailable"));
        if (!profile.online || !"APPROVED".equals(profile.onboardingStatus)) {
            throw new DomainException("PROVIDER.OFFLINE", "Approved provider must be online to accept offers");
        }
        Phase8Persistence.Offer offer = persistence.offer(offerId.toString())
                .orElseThrow(() -> new DomainException("OFFER.NOT_FOUND", "Provider offer was not found"));
        if (!offer.providerId.equals(providerId.toString())) throw accessDenied();
        if (!"PENDING".equals(offer.status)) {
            throw new DomainException("OFFER.ALREADY_DECIDED", "Provider offer has already been decided");
        }
        if (!offer.expiresAt.isAfter(clock.instant())) {
            offer.status = "EXPIRED";
            offer.updatedAt = clock.instant();
            persistence.saveOffer(offer);
            throw new DomainException("OFFER.EXPIRED", "Provider offer has expired");
        }
        Phase8Persistence.BookingView booking = requireView(PublicId.parse(offer.bookingId));
        if (command.estimatedAmountMinor() < booking.expectedAmountMinor / 2
                || command.estimatedAmountMinor() > Math.multiplyExact(booking.expectedAmountMinor, 2)) {
            throw new DomainException("OFFER.PRICE_OUT_OF_RANGE", "Provider estimate is outside the allowed range");
        }
        if (command.etaMinutes() < 5 || command.etaMinutes() > 240) {
            throw new DomainException("OFFER.ETA_INVALID", "ETA must be between 5 and 240 minutes");
        }
        offer.estimatedAmountMinor = command.estimatedAmountMinor();
        offer.etaMinutes = command.etaMinutes();
        offer.note = command.note() == null ? "" : command.note().trim();
        offer.status = "ACCEPTED_BY_PROVIDER";
        offer.updatedAt = clock.instant();
        return offerView(persistence.saveOffer(offer));
    }

    public BookingView selectProvider(PublicId customerId, PublicId bookingId, PublicId offerId) {
        Phase8Persistence.BookingView view = requireOwnedView(customerId, bookingId);
        Phase8Persistence.Offer selected = persistence.offer(offerId.toString())
                .orElseThrow(() -> new DomainException("OFFER.NOT_FOUND", "Provider offer was not found"));
        if (!selected.bookingId.equals(bookingId.toString())
                || !"ACCEPTED_BY_PROVIDER".equals(selected.status)
                || !selected.expiresAt.isAfter(clock.instant())) {
            throw new DomainException("BOOKING.OFFER_NOT_SELECTABLE", "Provider offer cannot be selected");
        }
        Booking booking = requireBooking(bookingId);
        booking.selectProvider(Actor.customer(customerId), PublicId.parse(selected.providerId), PublicId.generate());
        booking.openPaymentWindow(Actor.system(), PublicId.generate());
        booking = bookingPersistence.save(booking);
        for (Phase8Persistence.Offer offer : persistence.bookingOffers(bookingId.toString())) {
            if (offer.id.equals(selected.id)) offer.status = "SELECTED_BY_CUSTOMER";
            else if ("PENDING".equals(offer.status) || "ACCEPTED_BY_PROVIDER".equals(offer.status)) {
                offer.status = "NOT_SELECTED";
            }
            offer.updatedAt = clock.instant();
            persistence.saveOffer(offer);
        }
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    public BookingView confirmLocalTestPayment(PublicId customerId, PublicId bookingId) {
        if (!Set.of("local", "test").contains(environment.toLowerCase(Locale.ROOT))) {
            throw new DomainException("PAYMENT.LOCAL_TEST_DISABLED",
                    "Local test payment confirmation is unavailable outside local and test environments");
        }
        Phase8Persistence.BookingView view = requireOwnedView(customerId, bookingId);
        Booking booking = requireBooking(bookingId);
        if (booking.status() != BookingStatus.PAYMENT_PENDING) {
            throw new DomainException("BOOKING.INVALID_TRANSITION", "Booking is not waiting for payment");
        }
        PublicId holdId = PublicId.generate();
        Phase8Persistence.LocalTestHold hold = new Phase8Persistence.LocalTestHold();
        hold.id = holdId.toString();
        hold.bookingId = bookingId.toString();
        hold.customerId = customerId.toString();
        hold.amountMinor = booking.expectedPayment().amountMinor();
        hold.currency = booking.expectedPayment().currencyCode();
        hold.status = "TEST_HELD";
        hold.createdAt = clock.instant();
        persistence.insertLocalTestHold(hold);

        booking.recordVerifiedPayment(Actor.system(),
                new VerifiedPaymentEvidence(holdId, booking.expectedPayment(), true, true), PublicId.generate());
        booking.assignSelectedProvider(Actor.system(), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    @Transactional(readOnly = true)
    public List<BookingView> providerBookings(PublicId providerId) {
        requireRole(providerId, "PROVIDER");
        return persistence.providerBookings(providerId.toString()).stream().map(this::bookingView).toList();
    }

    public BookingView startJourney(PublicId providerId, PublicId bookingId) {
        return providerCommand(providerId, bookingId, Booking::startJourney);
    }

    public BookingView markArrived(PublicId providerId, PublicId bookingId) {
        return providerCommand(providerId, bookingId, Booking::markArrived);
    }

    public ChallengeView requestStartOtp(PublicId customerId, PublicId bookingId) {
        Phase8Persistence.BookingView view = requireOwnedView(customerId, bookingId);
        Booking booking = requireBooking(bookingId);
        long issuanceVersion = Math.addExact(booking.version(), 1);
        String subject = otpSubject(bookingId, "BOOKING_START");
        OtpDelivery issued = otps.issue(subject, com.localserve.identity.otp.OtpPurpose.BOOKING_START, issuanceVersion);
        booking.requestStartOtp(Actor.system(), issued.challengeId(), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        persistence.saveBookingView(view);
        sendOtp(customerId, bookingId, "Start OTP", issued);
        return new ChallengeView(issued.challengeId().toString(), "BOOKING_START", issued.expiresAt(),
                "EMAIL", booking.version());
    }

    public BookingView verifyStartOtp(PublicId providerId, PublicId bookingId, OtpCommand command) {
        Phase8Persistence.BookingView view = requireProviderView(providerId, bookingId);
        Booking booking = requireBooking(bookingId);
        PublicId challengeId = booking.startOtpChallengeId();
        if (challengeId == null) {
            throw new DomainException("AUTH.OTP_INVALID", "Start OTP challenge is unavailable");
        }
        String subject = otpSubject(bookingId, "BOOKING_START");
        otps.verify(challengeId, subject, com.localserve.identity.otp.OtpPurpose.BOOKING_START,
                command.code(), booking.version());
        booking.startService(Actor.provider(providerId),
                new VerifiedOtpEvidence(challengeId, bookingId, com.localserve.booking.domain.OtpPurpose.BOOKING_START,
                        booking.version(), true, true), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    public BookingView requestCompletion(PublicId providerId, PublicId bookingId, CompletionCommand command) {
        if (!command.afterEvidenceAcknowledged()) {
            throw new DomainException("BOOKING.COMPLETION_EVIDENCE_REQUIRED",
                    "Completion evidence acknowledgement is required");
        }
        Phase8Persistence.BookingView view = requireProviderView(providerId, bookingId);
        Booking booking = requireBooking(bookingId);
        booking.requestCompletion(Actor.provider(providerId), new CompletionEvidence(true, true), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    public ChallengeView requestCompletionOtp(PublicId customerId, PublicId bookingId) {
        Phase8Persistence.BookingView view = requireOwnedView(customerId, bookingId);
        Booking booking = requireBooking(bookingId);
        String subject = otpSubject(bookingId, "BOOKING_COMPLETION");
        OtpDelivery issued = otps.issue(subject, com.localserve.identity.otp.OtpPurpose.BOOKING_COMPLETION,
                booking.version());
        booking.registerCompletionOtp(Actor.system(), issued.challengeId());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        persistence.saveBookingView(view);
        sendOtp(customerId, bookingId, "Completion OTP", issued);
        return new ChallengeView(issued.challengeId().toString(), "BOOKING_COMPLETION", issued.expiresAt(),
                "EMAIL", booking.version());
    }

    public BookingView verifyCompletionOtp(PublicId providerId, PublicId bookingId, OtpCommand command) {
        Phase8Persistence.BookingView view = requireProviderView(providerId, bookingId);
        Booking booking = requireBooking(bookingId);
        PublicId challengeId = booking.completionOtpChallengeId();
        if (challengeId == null) {
            throw new DomainException("AUTH.OTP_INVALID", "Completion OTP challenge is unavailable");
        }
        String subject = otpSubject(bookingId, "BOOKING_COMPLETION");
        otps.verify(challengeId, subject, com.localserve.identity.otp.OtpPurpose.BOOKING_COMPLETION,
                command.code(), booking.version());
        booking.verifyCompletion(Actor.provider(providerId),
                new VerifiedOtpEvidence(challengeId, bookingId,
                        com.localserve.booking.domain.OtpPurpose.BOOKING_COMPLETION,
                        booking.version(), true, true), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    public BookingView confirmSatisfaction(PublicId customerId, PublicId bookingId) {
        Phase8Persistence.BookingView view = requireOwnedView(customerId, bookingId);
        Booking booking = requireBooking(bookingId);
        booking.confirmSatisfaction(Actor.customer(customerId), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    private BookingView providerCommand(PublicId providerId, PublicId bookingId, ProviderTransition command) {
        Phase8Persistence.BookingView view = requireProviderView(providerId, bookingId);
        Booking booking = requireBooking(bookingId);
        command.apply(booking, Actor.provider(providerId), PublicId.generate());
        booking = bookingPersistence.save(booking);
        sync(view, booking);
        return bookingView(persistence.saveBookingView(view));
    }

    private void sendOtp(PublicId customerId, PublicId bookingId, String purpose, OtpDelivery issued) {
        IdentityPersistence.Account customer = requireRole(customerId, "CUSTOMER");
        if (customer.normalizedEmail == null) {
            throw new DomainException("AUTH.DELIVERY_UNAVAILABLE", "Customer email is unavailable for OTP delivery");
        }
        delivery.sendBookingOtp(customer.normalizedEmail, bookingId.toString(), purpose,
                issued.plaintextCode(), issued.expiresAt());
    }

    private Phase8Persistence.BookingView requireOwnedView(PublicId customerId, PublicId bookingId) {
        requireRole(customerId, "CUSTOMER");
        Phase8Persistence.BookingView view = requireView(bookingId);
        if (!view.customerId.equals(customerId.toString())) throw accessDenied();
        return view;
    }

    private Phase8Persistence.BookingView requireProviderView(PublicId providerId, PublicId bookingId) {
        requireRole(providerId, "PROVIDER");
        Phase8Persistence.BookingView view = requireView(bookingId);
        if (!providerId.toString().equals(view.assignedProviderId)) throw accessDenied();
        return view;
    }

    private Phase8Persistence.BookingView requireView(PublicId bookingId) {
        return persistence.bookingView(bookingId.toString())
                .orElseThrow(() -> new DomainException("BOOKING.NOT_FOUND", "Booking was not found"));
    }

    private Booking requireBooking(PublicId bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new DomainException("BOOKING.NOT_FOUND", "Booking was not found"));
    }

    private IdentityPersistence.Account requireRole(PublicId accountId, String role) {
        IdentityPersistence.Account account = identities.findAccount(accountId)
                .orElseThrow(() -> new DomainException("ACCOUNT.NOT_FOUND", "Account was not found"));
        if (!account.roles.contains(role) || !account.isActive()) throw accessDenied();
        return account;
    }

    private ProviderView providerView(IdentityPersistence.Account account,
                                      Phase8Persistence.ProviderProfile profile) {
        return new ProviderView(account.id, account.displayName, profile.businessDisplayName,
                profile.serviceZoneId, profile.serviceCodes, profile.onboardingStatus,
                profile.online, profile.capacity, profile.updatedAt);
    }

    private Phase8Persistence.ProviderProfile draftProfile(IdentityPersistence.Account account) {
        Phase8Persistence.ProviderProfile profile = new Phase8Persistence.ProviderProfile();
        profile.providerId = account.id;
        profile.businessDisplayName = account.businessDisplayName;
        profile.serviceZoneId = account.primaryServiceZoneId;
        profile.onboardingStatus = account.providerOnboardingStatus == null ? "DRAFT" : account.providerOnboardingStatus;
        profile.capacity = 1;
        profile.updatedAt = account.updatedAt;
        return profile;
    }

    private QuoteView quoteView(Phase8Persistence.Quote quote) {
        return new QuoteView(quote.id, quote.serviceId, quote.serviceCode, quote.serviceName,
                quote.bookingType, quote.serviceZoneId, quote.amountMinor, quote.currency, quote.expiresAt);
    }

    private BookingView bookingView(Phase8Persistence.BookingView view) {
        String providerName = null;
        if (view.selectedProviderId != null) {
            providerName = identities.findAccount(PublicId.parse(view.selectedProviderId))
                    .map(account -> account.businessDisplayName == null ? account.displayName : account.businessDisplayName)
                    .orElse("Selected provider");
        }
        return new BookingView(view.id, view.serviceId, view.serviceCode, view.serviceName,
                view.bookingType, view.serviceZoneId, view.address, view.problemDescription,
                view.expectedAmountMinor, view.currency, view.status, view.aggregateVersion,
                view.selectedProviderId, providerName, view.createdAt, view.updatedAt);
    }

    private OfferView offerView(Phase8Persistence.Offer offer) {
        Phase8Persistence.BookingView booking = requireView(PublicId.parse(offer.bookingId));
        IdentityPersistence.Account provider = identities.findAccount(PublicId.parse(offer.providerId))
                .orElseThrow(() -> new DomainException("PROVIDER.NOT_FOUND", "Provider was not found"));
        String providerName = provider.businessDisplayName == null ? provider.displayName : provider.businessDisplayName;
        return new OfferView(offer.id, offer.bookingId, offer.providerId, providerName,
                booking.serviceCode, booking.serviceName, booking.serviceZoneId,
                booking.problemDescription, booking.expectedAmountMinor, booking.currency,
                offer.status, offer.estimatedAmountMinor, offer.etaMinutes, offer.note,
                offer.expiresAt, offer.updatedAt);
    }

    private void sync(Phase8Persistence.BookingView view, Booking booking) {
        view.status = booking.status().name();
        view.aggregateVersion = booking.version();
        view.selectedProviderId = booking.selectedProviderId() == null ? null : booking.selectedProviderId().toString();
        view.assignedProviderId = booking.assignedProviderId() == null ? null : booking.assignedProviderId().toString();
        view.updatedAt = clock.instant();
    }

    private ServiceDefinition service(String rawCode) {
        String code = normalizeServiceCode(rawCode);
        return CATALOG.stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new DomainException("CATALOG.NOT_FOUND", "Service was not found"));
    }

    private String normalizeServiceCode(String rawCode) {
        Objects.requireNonNull(rawCode, "serviceCode");
        return rawCode.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static BookingType parseBookingType(String value) {
        try {
            return BookingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new DomainException("BOOKING.TYPE_INVALID", "Booking type is invalid");
        }
    }

    private String otpSubject(PublicId bookingId, String purpose) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (subjectPepper + ":" + bookingId + ":" + purpose).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String value, int minimum, int maximum, String field) {
        if (value == null || value.trim().length() < minimum || value.trim().length() > maximum) {
            throw new DomainException("VALIDATION.FAILED", field + " has an invalid length");
        }
    }

    private static DomainException accessDenied() {
        return new DomainException("ACCESS.DENIED", "The account is not authorized for this action");
    }

    @FunctionalInterface
    private interface ProviderTransition {
        void apply(Booking booking, Actor actor, PublicId correlationId);
    }

    private record ServiceDefinition(String id, String code, String name, long baseAmountMinor) { }

    public record ServiceView(String id, String code, String name, long baseAmountMinor, String currency) { }
    public record ProviderCommand(String businessDisplayName, String serviceZoneId,
                                  Set<String> serviceCodes, int capacity) { }
    public record ProviderView(String id, String displayName, String businessDisplayName,
                               String serviceZoneId, Set<String> serviceCodes, String onboardingStatus,
                               boolean online, int capacity, Instant updatedAt) { }
    public record QuoteCommand(String serviceCode, String bookingType, String serviceZoneId) { }
    public record QuoteView(String id, String serviceId, String serviceCode, String serviceName,
                            String bookingType, String serviceZoneId, long amountMinor,
                            String currency, Instant expiresAt) { }
    public record CreateBookingCommand(String quoteId, String address, String problemDescription) { }
    public record BookingView(String id, String serviceId, String serviceCode, String serviceName,
                              String bookingType, String serviceZoneId, String address,
                              String problemDescription, long expectedAmountMinor, String currency,
                              String status, long version, String selectedProviderId,
                              String selectedProviderName, Instant createdAt, Instant updatedAt) { }
    public record OfferDecision(long estimatedAmountMinor, int etaMinutes, String note) { }
    public record OfferView(String id, String bookingId, String providerId, String providerName,
                            String serviceCode, String serviceName, String serviceZoneId,
                            String problemDescription, long expectedAmountMinor, String currency,
                            String status, Long estimatedAmountMinor, Integer etaMinutes,
                            String note, Instant expiresAt, Instant updatedAt) { }
    public record OtpCommand(String code) { }
    public record CompletionCommand(boolean afterEvidenceAcknowledged) { }
    public record ChallengeView(String challengeId, String purpose, Instant expiresAt,
                                String deliveryChannel, long bookingVersion) { }
}
