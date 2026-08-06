package com.localserve.identityinfra;

import com.localserve.identity.password.BreachedPasswordChecker;
import com.localserve.shared.error.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Uses a SHA-1 prefix only; the password and complete digest never leave this process. */
public final class KAnonymityBreachedPasswordChecker implements BreachedPasswordChecker {
    private final RestClient client;

    public KAnonymityBreachedPasswordChecker(RestClient.Builder builder, String baseUrl) {
        this.client = builder.baseUrl(baseUrl).defaultHeader(HttpHeaders.USER_AGENT, "LocalServe-Password-Screen/1.0")
                .defaultHeader("Add-Padding", "true").build();
    }

    @Override public boolean isKnownBreached(char[] password) {
        byte[] utf8 = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            String digest = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(utf8));
            String prefix = digest.substring(0, 5), suffix = digest.substring(5);
            String response = client.get().uri(prefix).retrieve().body(String.class);
            if (response == null) throw unavailable();
            return response.lines().map(line -> line.split(":", 2)[0]).anyMatch(suffix::equals);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is unavailable", exception);
        } catch (RestClientException exception) {
            throw unavailable();
        } finally {
            Arrays.fill(utf8, (byte) 0);
        }
    }

    private static DomainException unavailable() {
        return new DomainException("AUTH.PASSWORD_SCREENING_UNAVAILABLE", "Password screening is temporarily unavailable");
    }
}
