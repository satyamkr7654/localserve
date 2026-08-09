package com.localserve.identityapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMfaServiceTest {
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void verifiesTotpWithinTheAllowedWindow() {
        assertThat(AdminMfaService.validTotp(RFC_SECRET, "287082", 59_000L)).isTrue();
    }

    @Test
    void rejectsMalformedAndIncorrectCodes() {
        assertThat(AdminMfaService.validTotp(RFC_SECRET, "287083", 59_000L)).isFalse();
        assertThat(AdminMfaService.validTotp(RFC_SECRET, "abc", 59_000L)).isFalse();
    }
}
