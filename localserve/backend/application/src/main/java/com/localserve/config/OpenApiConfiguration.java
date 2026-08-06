package com.localserve.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean OpenAPI localServeOpenApi() {
        return new OpenAPI().info(new Info().title("LocalServe API").version("v1"))
                .components(new Components().addSecuritySchemes("bearerJwt",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
    @Bean GroupedOpenApi publicApi() { return GroupedOpenApi.builder().group("public").pathsToMatch("/api/v1/public/**", "/api/v1/catalog/**", "/api/v1/search/**").build(); }
    @Bean GroupedOpenApi customerApi() { return GroupedOpenApi.builder().group("customer").pathsToMatch("/api/v1/customer/**").build(); }
    @Bean GroupedOpenApi providerApi() { return GroupedOpenApi.builder().group("provider").pathsToMatch("/api/v1/provider/**").build(); }
    @Bean GroupedOpenApi adminApi() { return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/v1/admin/**").build(); }
}
