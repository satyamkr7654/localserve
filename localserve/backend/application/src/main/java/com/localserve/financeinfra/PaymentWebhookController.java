package com.localserve.financeinfra;

import com.localserve.finance.webhook.RawWebhookRequest;
import com.localserve.finance.webhook.WebhookIngressResult;
import com.localserve.finance.webhook.WebhookIngressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/webhooks")
public class PaymentWebhookController {
    private final WebhookIngressService ingress;
    public PaymentWebhookController(WebhookIngressService ingress) { this.ingress = ingress; }

    @PostMapping("/razorpay")
    ResponseEntity<Void> razorpay(@RequestBody byte[] body,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader("X-Razorpay-Event-Id") String eventId) {
        return accepted(ingress.ingest(new RawWebhookRequest("RAZORPAY", body,
                Map.of("X-Razorpay-Signature", signature, "X-Razorpay-Event-Id", eventId))));
    }

    @PostMapping("/stripe")
    ResponseEntity<Void> stripe(@RequestBody byte[] body, @RequestHeader("Stripe-Signature") String signature) {
        return accepted(ingress.ingest(new RawWebhookRequest("STRIPE", body, Map.of("Stripe-Signature", signature))));
    }

    private static ResponseEntity<Void> accepted(WebhookIngressResult ignored) {
        return ResponseEntity.accepted().build();
    }
}
