package org.dromara.payment.security;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class AccountRecoveryCodeHasherTest {

    @Test
    void hashIsDeterministicAndBoundToChallengeContext() {
        AccountRecoveryCodeHasher hasher = hasher(
            "0123456789abcdef0123456789abcdef"
        );

        String first = hasher.hash(
            "challenge-a",
            "PASSWORD_RESET",
            "123456"
        );
        String repeated = hasher.hash(
            "challenge-a",
            "PASSWORD_RESET",
            "123456"
        );
        String differentChallenge = hasher.hash(
            "challenge-b",
            "PASSWORD_RESET",
            "123456"
        );

        assertEquals(64, first.length());
        assertEquals(first, repeated);
        assertNotEquals(first, differentChallenge);
        assertTrue(hasher.matches(
            first,
            "challenge-a",
            "PASSWORD_RESET",
            "123456"
        ));
        assertFalse(hasher.matches(
            first,
            "challenge-a",
            "PASSWORD_RESET",
            "654321"
        ));
    }

    @Test
    void shortPepperIsRejected() {
        AccountRecoveryCodeHasher hasher = hasher("too-short");

        assertThrows(
            ServiceException.class,
            () -> hasher.hash(
                "challenge-a",
                "PASSWORD_RESET",
                "123456"
            )
        );
    }

    private AccountRecoveryCodeHasher hasher(String pepper) {
        PaymentProperties properties = new PaymentProperties();
        properties.getAccountRecovery().setCodePepper(pepper);
        return new AccountRecoveryCodeHasher(properties);
    }
}
