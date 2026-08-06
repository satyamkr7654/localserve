package com.localserve.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
public class JwtAuthorityConfiguration {
    @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
        var scopeConverter = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.stream().filter(JwtAuthorityConfiguration::validRole)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> realmRoles) {
                realmRoles.stream().filter(String.class::isInstance).map(String.class::cast)
                        .filter(JwtAuthorityConfiguration::validRole)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
            }
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) permissions.stream().filter(value -> value.matches("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+"))
                    .map(SimpleGrantedAuthority::new).forEach(authorities::add);
            return List.copyOf(authorities);
        });
        return converter;
    }

    private static boolean validRole(String role) {
        return "CUSTOMER".equals(role) || "PROVIDER".equals(role) || "ADMIN".equals(role);
    }
}
