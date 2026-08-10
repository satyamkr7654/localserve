package com.localserve.phase8;

import com.localserve.shared.identity.PublicId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/provider")
public class Phase8ProviderController {
    private final Phase8BookingService service;

    public Phase8ProviderController(Phase8BookingService service) {
        this.service = service;
    }

    @GetMapping("/onboarding")
    ResponseEntity<Phase8BookingService.ProviderView> onboarding(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.providerOnboarding(principal(jwt)));
    }

    @PatchMapping("/onboarding")
    ResponseEntity<Phase8BookingService.ProviderView> saveOnboarding(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProviderProfileRequest body) {
        return ok(service.saveOnboarding(principal(jwt), new Phase8BookingService.ProviderCommand(
                body.businessDisplayName(), body.serviceZoneId(), body.serviceCodes(), body.capacity())));
    }

    @PostMapping("/onboarding-submissions")
    ResponseEntity<Phase8BookingService.ProviderView> submitOnboarding(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProviderProfileRequest body) {
        return ok(service.saveOnboarding(principal(jwt), new Phase8BookingService.ProviderCommand(
                body.businessDisplayName(), body.serviceZoneId(), body.serviceCodes(), body.capacity())));
    }

    @GetMapping("/operational-status")
    ResponseEntity<Phase8BookingService.ProviderView> operationalStatus(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.providerOnboarding(principal(jwt)));
    }

    @PostMapping("/online-transitions")
    ResponseEntity<Phase8BookingService.ProviderView> setOnline(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OnlineRequest body) {
        return ok(service.setOnline(principal(jwt), body.online()));
    }

    @GetMapping("/offers")
    ResponseEntity<List<Phase8BookingService.OfferView>> offers(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.providerOffers(principal(jwt)));
    }

    @PostMapping("/offers/{offerId}/acceptances")
    ResponseEntity<Phase8BookingService.OfferView> acceptOffer(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String offerId,
            @Valid @RequestBody OfferAcceptanceRequest body) {
        return ok(service.acceptOffer(principal(jwt), PublicId.parse(offerId),
                new Phase8BookingService.OfferDecision(
                        body.estimatedAmountMinor(), body.etaMinutes(), body.note())));
    }

    @GetMapping("/bookings")
    ResponseEntity<List<Phase8BookingService.BookingView>> bookings(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.providerBookings(principal(jwt)));
    }

    @PostMapping("/bookings/{bookingId}/journey-starts")
    ResponseEntity<Phase8BookingService.BookingView> startJourney(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ok(service.startJourney(principal(jwt), PublicId.parse(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/arrivals")
    ResponseEntity<Phase8BookingService.BookingView> markArrived(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ok(service.markArrived(principal(jwt), PublicId.parse(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/start-otp-verifications")
    ResponseEntity<Phase8BookingService.BookingView> verifyStartOtp(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId,
            @Valid @RequestBody OtpRequest body) {
        return ok(service.verifyStartOtp(principal(jwt), PublicId.parse(bookingId),
                new Phase8BookingService.OtpCommand(body.code())));
    }

    @PostMapping("/bookings/{bookingId}/completion-requests")
    ResponseEntity<Phase8BookingService.BookingView> requestCompletion(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId,
            @Valid @RequestBody CompletionRequest body) {
        return ok(service.requestCompletion(principal(jwt), PublicId.parse(bookingId),
                new Phase8BookingService.CompletionCommand(body.afterEvidenceAcknowledged())));
    }

    @PostMapping("/bookings/{bookingId}/completion-otp-verifications")
    ResponseEntity<Phase8BookingService.BookingView> verifyCompletionOtp(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId,
            @Valid @RequestBody OtpRequest body) {
        return ok(service.verifyCompletionOtp(principal(jwt), PublicId.parse(bookingId),
                new Phase8BookingService.OtpCommand(body.code())));
    }

    private static PublicId principal(Jwt jwt) {
        return PublicId.parse(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record ProviderProfileRequest(@NotBlank @Size(min = 2, max = 120) String businessDisplayName,
                                  @NotBlank @Size(min = 2, max = 80) String serviceZoneId,
                                  @NotEmpty Set<@NotBlank @Size(max = 80) String> serviceCodes,
                                  @Min(1) @Max(5) int capacity) { }

    record OnlineRequest(boolean online) { }

    record OfferAcceptanceRequest(@Positive long estimatedAmountMinor,
                                  @Min(1) @Max(240) int etaMinutes,
                                  @Size(max = 500) String note) { }

    record OtpRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }

    record CompletionRequest(boolean afterEvidenceAcknowledged) { }
}
