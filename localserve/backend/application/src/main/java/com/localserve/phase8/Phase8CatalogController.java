package com.localserve.phase8;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/services")
public class Phase8CatalogController {
    private final Phase8BookingService service;

    public Phase8CatalogController(Phase8BookingService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<List<Phase8BookingService.ServiceView>> services() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(service.catalog());
    }
}
