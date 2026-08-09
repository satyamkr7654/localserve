package com.localserve.config;

import com.localserve.identity.otp.OtpChallengeStore;
import com.localserve.identity.otp.OtpService;
import com.localserve.identity.password.BreachedPasswordChecker;
import com.localserve.identity.password.PasswordService;
import com.localserve.identity.session.RefreshTokenRepository;
import com.localserve.identity.session.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import com.localserve.identityinfra.KAnonymityBreachedPasswordChecker;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class IdentityConfiguration {
    @Bean BreachedPasswordChecker breachedPasswordChecker(RestClient.Builder builder,
            @Value("${BREACHED_PASSWORD_CHECK_ENABLED:true}") boolean enabled,
            @Value("${BREACHED_PASSWORD_API_URL:https://api.pwnedpasswords.com/range/}") String baseUrl) {
        return enabled ? new KAnonymityBreachedPasswordChecker(builder, baseUrl) : password -> false;
    }
    @Bean OtpService otpService(OtpChallengeStore store, Clock clock,
                               @Value("${OTP_HMAC_PEPPER}") String pepper) {
        return new OtpService(store, clock, new SecureRandom(), pepper.getBytes(StandardCharsets.UTF_8), Duration.ofMinutes(5), 5);
    }
    @Bean RefreshTokenService refreshTokenService(RefreshTokenRepository repository, Clock clock,
                                                  LocalServeProperties properties,
                                                  @Value("${REFRESH_TOKEN_PEPPER}") String pepper) {
        return new RefreshTokenService(repository, clock, new SecureRandom(), pepper.getBytes(StandardCharsets.UTF_8),
                properties.security().refreshTokenTtl(), properties.security().rememberedRefreshTokenTtl());
    }
    @Bean PasswordService passwordService(BreachedPasswordChecker checker) { return new PasswordService(checker); }
}
