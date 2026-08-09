package com.localserve.identityapi;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Configuration
public class JwtKeyConfiguration {
    @Bean
    RsaKeyMaterial localServeRsaKey(
            @Value("${JWT_PRIVATE_KEY_BASE64:}") String privateKey,
            @Value("${JWT_PUBLIC_KEY_BASE64:}") String publicKey,
            @Value("${APP_ENVIRONMENT:local}") String environment) {
        if (!privateKey.isBlank() && !publicKey.isBlank()) return load(privateKey, publicKey);
        if (!"local".equalsIgnoreCase(environment) && !"test".equalsIgnoreCase(environment)) {
            throw new IllegalStateException("JWT signing keys are required outside local/test environments");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var pair = generator.generateKeyPair();
            return material((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate the local JWT signing key", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RsaKeyMaterial material) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(material.rsaKey())));
    }

    @Bean
    JwtDecoder jwtDecoder(RsaKeyMaterial material,
                          @Value("${JWT_ISSUER:https://api.localserve.example}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(material.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private static RsaKeyMaterial load(String privateValue, String publicValue) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(clean(privateValue))));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(clean(publicValue))));
            return material(publicKey, privateKey);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT signing keys are invalid", exception);
        }
    }

    private static RsaKeyMaterial material(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(UUID.randomUUID().toString()).build();
        return new RsaKeyMaterial(publicKey, rsaKey);
    }

    private static String clean(String value) {
        return value.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    record RsaKeyMaterial(RSAPublicKey publicKey, RSAKey rsaKey) {
        Map<String, Object> publicJwkSet() {
            return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
        }
    }
}
