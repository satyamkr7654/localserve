package com.localserve.phase8;

import com.localserve.shared.identity.PublicId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
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
@RequestMapping("/api/v1/admin")
public class Phase8AdminController {
    private final Phase8BookingService service;

    public Phase8AdminController(Phase8BookingService service) {
        this.service = service;
    }

    @GetMapping("/verification-requests")
    ResponseEntity<List<Phase8BookingService.ProviderView>> verificationRequests(
            @AuthenticationPrincipal Jwt jwt) {
        return ok(service.verificationQueue(principal(jwt)));
    }

    @PostMapping("/verification-requests/{providerId}/decisions")
    ResponseEntity<Phase8BookingService.ProviderView> decide(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String providerId,
            @Valid @RequestBody DecisionRequest body) {
        return ok(service.approveProvider(principal(jwt), PublicId.parse(providerId),
                body.approved(), body.reason()));
    }

    private static PublicId principal(Jwt jwt) {
        return PublicId.parse(jwt.getSubject());
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    record DecisionRequest(@NotNull Boolean approved,
                           @NotBlank @Size(min = 3, max = 500) String reason) { }
}
