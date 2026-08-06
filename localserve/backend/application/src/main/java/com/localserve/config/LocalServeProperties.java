package com.localserve.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("localserve")
public record LocalServeProperties(@Valid Security security, @Valid Runtime runtime) {
    public record Security(
            @NotEmpty List<@NotBlank String> allowedOrigins,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotNull Duration rememberedRefreshTokenTtl,
            @NotBlank String refreshCookieName,
            boolean secureCookies) { }

    public record Runtime(
            @NotBlank String environment,
            @Min(1) @Max(1000) int outboxBatchSize,
            @NotNull Duration shutdownTimeout) { }
}
