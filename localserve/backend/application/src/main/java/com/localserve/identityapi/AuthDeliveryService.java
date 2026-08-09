package com.localserve.identityapi;

import com.localserve.shared.error.DomainException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthDeliveryService {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<LocalOtpMailbox> localMailbox;
    private final RestClient restClient;
    private final String smsGatewayUrl;
    private final String smsGatewayToken;
    private final String smsSenderId;
    private final String emailFrom;
    private final String verificationBaseUrl;
    private final String recoveryBaseUrl;

    public AuthDeliveryService(ObjectProvider<JavaMailSender> mailSender,
                               ObjectProvider<LocalOtpMailbox> localMailbox,
                               RestClient.Builder builder,
                               @Value("${SMS_GATEWAY_URL:}") String smsGatewayUrl,
                               @Value("${SMS_GATEWAY_TOKEN:}") String smsGatewayToken,
                               @Value("${SMS_SENDER_ID:LocalServe}") String smsSenderId,
                               @Value("${AUTH_EMAIL_FROM:no-reply@localserve.example}") String emailFrom,
                               @Value("${EMAIL_VERIFICATION_BASE_URL:http://localhost:3000/verify-email}") String verificationBaseUrl,
                               @Value("${PASSWORD_RECOVERY_BASE_URL:http://localhost:3000/reset-password}") String recoveryBaseUrl) {
        this.mailSender = mailSender;
        this.localMailbox = localMailbox;
        this.restClient = builder.build();
        this.smsGatewayUrl = smsGatewayUrl;
        this.smsGatewayToken = smsGatewayToken;
        this.smsSenderId = smsSenderId;
        this.emailFrom = emailFrom;
        this.verificationBaseUrl = verificationBaseUrl;
        this.recoveryBaseUrl = recoveryBaseUrl;
    }

    public void sendOtp(String phone, String code, Instant expiresAt) {
        LocalOtpMailbox mailbox = localMailbox.getIfAvailable();
        if (mailbox != null) {
            mailbox.deliver(phone, code, expiresAt);
            return;
        }
        if (smsGatewayUrl.isBlank() || smsGatewayToken.isBlank()) throw unavailable();
        restClient.post().uri(smsGatewayUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(smsGatewayToken))
                .body(Map.of("to", phone, "senderId", smsSenderId, "template", "LOCAL_SERVE_AUTH_OTP",
                        "variables", Map.of("code", code, "expiresAt", expiresAt.toString())))
                .retrieve().toBodilessEntity();
    }

    public void sendEmailVerification(String email, String token) {
        sendEmail(email, "Verify your LocalServe email",
                "Complete verification using this secure, single-use link:\n" + verificationBaseUrl + "?token=" + token);
    }

    public void sendPasswordRecovery(String email, String token) {
        sendEmail(email, "Reset your LocalServe password",
                "If you requested a password reset, use this single-use link:\n" + recoveryBaseUrl + "?token=" + token);
    }

    private void sendEmail(String email, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw unavailable();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    private static DomainException unavailable() {
        return new DomainException("AUTH.DELIVERY_UNAVAILABLE", "Verification delivery is temporarily unavailable");
    }
}

@Component
@Profile({"local", "test"})
class LocalOtpMailbox {
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    LocalOtpMailbox(Clock clock) { this.clock = clock; }
    void deliver(String phone, String code, Instant expiresAt) { entries.put(phone, new Entry(code, expiresAt)); }

    Optional<String> read(String phone) {
        Entry entry = entries.get(phone);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(phone);
            return Optional.empty();
        }
        return Optional.of(entry.code());
    }

    private record Entry(String code, Instant expiresAt) { }
}
