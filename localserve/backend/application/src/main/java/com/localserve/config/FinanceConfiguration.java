package com.localserve.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.finance.webhook.*;
import com.localserve.financeinfra.JacksonWebhookMetadataExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@Configuration
public class FinanceConfiguration {
    @Bean WebhookSignatureVerifier webhookSignatureVerifier(
            @Value("${RAZORPAY_WEBHOOK_SECRET:}") String razorpaySecret,
            @Value("${STRIPE_WEBHOOK_SECRET:}") String stripeSecret,
            @Value("${APP_ENVIRONMENT:local}") String environment,
            Clock clock) {
        return new CompositeWebhookSignatureVerifier(Map.of(
                "RAZORPAY", new RazorpayWebhookSignatureVerifier(secret(razorpaySecret, environment, "razorpay")),
                "STRIPE", new StripeWebhookSignatureVerifier(
                        secret(stripeSecret, environment, "stripe"), clock, Duration.ofMinutes(5))));
    }

    private static byte[] secret(String configured, String environment, String provider) {
        String value = configured;
        if (value.isBlank() && ("local".equalsIgnoreCase(environment) || "test".equalsIgnoreCase(environment))) {
            value = "local-only-" + provider + "-webhook-secret-change-before-production";
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Bean VerifiedWebhookMetadataExtractor webhookMetadataExtractor(ObjectMapper json) {
        return new JacksonWebhookMetadataExtractor(json);
    }

    @Bean WebhookIngressService webhookIngressService(WebhookSignatureVerifier verifier,
            VerifiedWebhookMetadataExtractor extractor, WebhookReceiptRepository repository, Clock clock) {
        return new WebhookIngressService(verifier, extractor, repository, clock);
    }
}
