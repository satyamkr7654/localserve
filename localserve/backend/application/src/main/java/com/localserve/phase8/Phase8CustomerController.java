package com.localserve.phase8;

import com.localserve.shared.identity.PublicId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer")
public class Phase8CustomerController {
    private final Phase8BookingService service;

    public Phase8CustomerController(Phase8BookingService service) {
        this.service = service;
    }

    @PostMapping("/booking-quotes")
    ResponseEntity<Phase8BookingService.QuoteView> createQuote(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuoteRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.createQuote(principal(jwt),
                        new Phase8BookingService.QuoteCommand(
                                body.serviceCode(), body.bookingType(), body.serviceZoneId())));
    }

    @PostMapping("/bookings")
    ResponseEntity<Phase8BookingService.BookingView> createBooking(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.createBooking(principal(jwt),
                        new Phase8BookingService.CreateBookingCommand(
                                body.quoteId(), body.address(), body.problemDescription())));
    }

    @GetMapping("/bookings")
    ResponseEntity<List<Phase8BookingService.BookingView>> bookings(@AuthenticationPrincipal Jwt jwt) {
        return ok(service.customerBookings(principal(jwt)));
    }

    @GetMapping("/bookings/{bookingId}")
    ResponseEntity<Phase8BookingService.BookingView> booking(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ok(service.customerBooking(principal(jwt), PublicId.parse(bookingId)));
    }

    @GetMapping("/bookings/{bookingId}/offers")
    ResponseEntity<List<Phase8BookingService.OfferView>> offers(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ok(service.customerOffers(principal(jwt), PublicId.parse(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/provider-selections")
    ResponseEntity<Phase8BookingService.BookingView> selectProvider(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId,
            @Valid @RequestBody ProviderSelectionRequest body) {
        return ok(service.selectProvider(principal(jwt), PublicId.parse(bookingId),
                PublicId.parse(body.offerId())));
    }

    @PostMapping("/bookings/{bookingId}/local-test-payment-confirmations")
    ResponseEntity<Phase8BookingService.BookingView> confirmLocalTestPayment(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ok(service.confirmLocalTestPayment(principal(jwt), PublicId.parse(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/start-otp-challenges")
    ResponseEntity<Phase8BookingService.ChallengeView> requestStartOtp(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(service.requestStartOtp(principal(jwt), PublicId.parse(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/completion-otp-challenges")
    ResponseEntity<Phase8BookingService.ChallengeView> requestCompletionOtp(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(service.requestCompletionOtp(principal(jwt), PublicId.parse(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/satisfaction-confirmations")
    ResponseEntity<Phase8BookingService.BookingView> confirmSatisfaction(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String bookingId) {
        return ok(service.confirmSatisfaction(principal(jwt), PublicId.parse(bookingId)));
    }

    private static PublicId principal(Jwt jwt) {
        return PublicId.parse(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record QuoteRequest(@NotBlank @Size(max = 80) String serviceCode,
                        @NotBlank @Pattern(regexp = "INSTANT|SCHEDULED|EMERGENCY") String bookingType,
                        @NotBlank @Size(max = 80) String serviceZoneId) { }

    record CreateBookingRequest(@NotBlank String quoteId,
                                @NotBlank @Size(min = 5, max = 500) String address,
                                @NotBlank @Size(min = 5, max = 2000) String problemDescription) { }

    record ProviderSelectionRequest(@NotBlank String offerId) { }
}
