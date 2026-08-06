package com.localserve.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
public class PublicPlatformController {
    private final String version;
    public PublicPlatformController(@Value("${spring.application.version:dev}") String version) { this.version = version; }
    @GetMapping("/platform-status") public Map<String, String> platform() {
        return Map.of("name", "LocalServe", "apiVersion", "v1", "buildVersion", version);
    }
}
