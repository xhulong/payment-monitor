package org.dromara.payment.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class WebhookLogSanitizerTest {

    private final WebhookLogSanitizer sanitizer = new WebhookLogSanitizer();

    @Test
    void redactsCredentialsAndPhoneNumbers() {
        String value = sanitizer.sanitize(
            "Authorization: Bearer abcdefghijk token=secret-value phone=13812345678",
            1000);

        assertFalse(value.contains("abcdefghijk"));
        assertFalse(value.contains("secret-value"));
        assertFalse(value.contains("13812345678"));
        assertTrue(value.contains("138****5678"));
    }

    @Test
    void truncatesLongResponses() {
        String value = sanitizer.sanitize("x".repeat(100), 20);
        assertTrue(value.startsWith("x".repeat(20)));
        assertTrue(value.endsWith("[truncated]"));
    }
}
