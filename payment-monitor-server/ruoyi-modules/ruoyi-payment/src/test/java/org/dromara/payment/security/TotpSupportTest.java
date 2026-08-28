package org.dromara.payment.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class TotpSupportTest {

    @Test
    void acceptsCurrentAndAdjacentWindowAndReturnsMatchedStep() {
        String secret = TotpSupport.newSecret();
        long step = 1_900_000_000L;

        assertEquals(step, TotpSupport.matchingStep(
            secret, TotpSupport.code(secret, step), step));
        assertEquals(step - 1, TotpSupport.matchingStep(
            secret, TotpSupport.code(secret, step - 1), step));
        assertEquals(step + 1, TotpSupport.matchingStep(
            secret, TotpSupport.code(secret, step + 1), step));
        assertNull(TotpSupport.matchingStep(
            secret, TotpSupport.code(secret, step + 2), step));
    }

    @Test
    void rejectsMalformedCodes() {
        String secret = TotpSupport.newSecret();
        assertNull(TotpSupport.matchingStep(secret, null, 10));
        assertNull(TotpSupport.matchingStep(secret, "12345", 10));
        assertNull(TotpSupport.matchingStep(secret, "abcdef", 10));
    }

    @Test
    void base32RoundTripPreservesSecretBytes() {
        byte[] input = new byte[]{0, 1, 2, 3, 4, 5, 100, -1};
        String encoded = TotpSupport.encodeBase32(input);
        assertArrayEquals(input, TotpSupport.decodeBase32(encoded));
    }
}
