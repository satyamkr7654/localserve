package com.localserve.security;

import com.localserve.config.LocalServeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean @Order(1)
    SecurityFilterChain adminSecurity(HttpSecurity http, SecurityProblemWriter problems,
                                      JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http.securityMatcher("/api/v1/admin/**")
                .cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true)))
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemWriter problems,
                                    JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/api/v1/public/**", "/api/v1/integrations/webhooks/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**", "/api/v1/search/**").permitAll()
                        .requestMatchers("/api/v1/provider/**").hasAnyRole("PROVIDER", "ADMIN")
                        .requestMatchers("/api/v1/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true)))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(LocalServeProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.security().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID"));
        cors.setExposedHeaders(List.of("X-Correlation-ID", "Location"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
