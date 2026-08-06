package com.localserve.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localserve.finance.webhook.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.localserve.financeinfra.JacksonWebhookMetadataExtractor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@Configuration
public class FinanceConfiguration {
    @Bean WebhookSignatureVerifier webhookSignatureVerifier(
            @Value("${RAZORPAY_WEBHOOK_SECRET}") String razorpaySecret,
            @Value("${STRIPE_WEBHOOK_SECRET}") String stripeSecret, Clock clock) {
        return new CompositeWebhookSignatureVerifier(Map.of(
                "RAZORPAY", new RazorpayWebhookSignatureVerifier(razorpaySecret.getBytes(StandardCharsets.UTF_8)),
                "STRIPE", new StripeWebhookSignatureVerifier(stripeSecret.getBytes(StandardCharsets.UTF_8), clock, Duration.ofMinutes(5))));
    }
    @Bean VerifiedWebhookMetadataExtractor webhookMetadataExtractor(ObjectMapper json) {
        return new JacksonWebhookMetadataExtractor(json);
    }
    @Bean WebhookIngressService webhookIngressService(WebhookSignatureVerifier verifier,
            VerifiedWebhookMetadataExtractor extractor, WebhookReceiptRepository repository, Clock clock) {
        return new WebhookIngressService(verifier, extractor, repository, clock);
    }
}
