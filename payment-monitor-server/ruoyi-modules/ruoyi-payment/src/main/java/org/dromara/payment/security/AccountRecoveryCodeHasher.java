package org.dromara.payment.security;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class AccountRecoveryCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final PaymentProperties properties;

    public String hash(String challengeId, String challengeType, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key(), ALGORITHM));
            byte[] digest = mac.doFinal(
                payload(challengeId, challengeType, code)
                    .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Account recovery code hashing failed",
                e
            );
        }
    }

    public boolean matches(
        String expectedHash,
        String challengeId,
        String challengeType,
        String code
    ) {
        if (expectedHash == null || expectedHash.length() != 64) {
            return false;
        }
        byte[] expected;
        byte[] actual;
        try {
            expected = HexFormat.of().parseHex(expectedHash);
            actual = HexFormat.of().parseHex(
                hash(challengeId, challengeType, code)
            );
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] key() {
        String pepper = properties.getAccountRecovery().getCodePepper();
        if (pepper == null
            || pepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ServiceException(
                "Account recovery code pepper must be at least 32 bytes"
            );
        }
        return pepper.getBytes(StandardCharsets.UTF_8);
    }

    private String payload(
        String challengeId,
        String challengeType,
        String code
    ) {
        return challengeType
            + "\n"
            + challengeId
            + "\n"
            + (code == null ? "" : code.trim());
    }
}
