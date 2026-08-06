package com.localserve.finance.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

final class HmacSupport {
    private HmacSupport() {
    }

    static byte[] sha256(byte[] secret, byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    static boolean constantTimeHexEquals(byte[] expected, String providedHex) {
        if (providedHex == null || providedHex.length() != expected.length * 2) {
            return false;
        }
        try {
            byte[] provided = HexFormat.of().parseHex(providedHex);
            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
