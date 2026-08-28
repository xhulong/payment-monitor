package org.dromara.payment.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class PaymentCryptoTest {

    @Test
    void sha256SupportsRawBytesWithoutChangingCanonicalBody() {
        byte[] body = "{\"amountMinor\":10001}".getBytes(StandardCharsets.UTF_8);

        assertEquals(PaymentCrypto.sha256Hex(new String(body, StandardCharsets.UTF_8)),
            PaymentCrypto.sha256Hex(body));
        assertEquals(64, PaymentCrypto.sha256Hex(body).length());
    }

    @Test
    void hmacComparisonIsCaseInsensitiveAndRejectsDifferentValues() {
        String signature = PaymentCrypto.hmacSha256Hex("device-secret", "POST\n/path\n1\nnonce\nhash");

        assertTrue(PaymentCrypto.constantTimeEquals(signature, signature.toUpperCase()));
        assertFalse(PaymentCrypto.constantTimeEquals(signature, signature.substring(2) + "00"));
        assertFalse(PaymentCrypto.constantTimeEquals(signature, null));
    }

    @Test
    void exactComparisonKeepsBusinessKeysCaseSensitive() {
        assertTrue(PaymentCrypto.constantTimeEqualsExact("BusinessKeyAbC", "BusinessKeyAbC"));
        assertFalse(PaymentCrypto.constantTimeEqualsExact("BusinessKeyAbC", "businesskeyabc"));
        assertFalse(PaymentCrypto.constantTimeEqualsExact("BusinessKeyAbC", null));
    }

    @Test
    void generatedSecretsAndPairingCodesHaveExpectedShape() {
        assertTrue(PaymentCrypto.randomSecret().length() >= 40);
        assertTrue(PaymentCrypto.randomPairingCode().matches("\\d{8}"));
    }
}
