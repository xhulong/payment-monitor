package org.dromara.payment.integration.epay.security;

import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class EpaySecurityTest {

    @Test
    void encryptsSecretsWithRandomIvAndSupportsMasterKeyRotation() {
        PaymentProperties properties = new PaymentProperties();
        properties.getEasyPay().setActiveKeyId("master-v1");
        properties.getEasyPay().setActiveKey("master-key-v1-with-enough-entropy-for-tests");
        EpaySecretCipher cipher = new EpaySecretCipher(properties);

        EpaySecretCipher.EncryptedSecret first = cipher.encrypt("BusinessKeyAbC");
        EpaySecretCipher.EncryptedSecret second = cipher.encrypt("BusinessKeyAbC");

        assertNotEquals(first.cipherText(), second.cipherText());
        assertEquals("BusinessKeyAbC", cipher.decrypt(first.cipherText(), "master-v1"));

        properties.getEasyPay().setPreviousKeyId("master-v1");
        properties.getEasyPay().setPreviousKey("master-key-v1-with-enough-entropy-for-tests");
        properties.getEasyPay().setActiveKeyId("master-v2");
        properties.getEasyPay().setActiveKey("master-key-v2-with-enough-entropy-for-tests");

        assertEquals("BusinessKeyAbC", cipher.decrypt(first.cipherText(), "master-v1"));
        EpaySecretCipher.EncryptedSecret current = cipher.encrypt("CurrentBusinessKey");
        assertEquals("master-v2", current.keyId());
        assertEquals("CurrentBusinessKey", cipher.decrypt(current.cipherText(), "master-v2"));
        assertThrows(IllegalStateException.class,
            () -> cipher.decrypt(current.cipherText(), "unknown-master"));
    }

    @Test
    void validatesCallbackWhitelistAndDevelopmentOverrides() {
        PaymentProperties properties = new PaymentProperties();
        EpayUrlValidator validator = new EpayUrlValidator(properties);

        assertEquals("merchant.example,callback.example",
            validator.normalizeHosts(List.of("Merchant.Example", "callback.example")));
        assertThrows(EpayException.class,
            () -> validator.validate("http://merchant.example/callback", "merchant.example"));
        assertThrows(EpayException.class,
            () -> validator.validate("https://other.example/callback", "merchant.example"));
        assertThrows(EpayException.class,
            () -> validator.validate("https://user:pass@merchant.example/callback", "merchant.example"));

        properties.getEasyPay().setAllowHttp(true);
        properties.getEasyPay().setAllowPrivateNetwork(true);
        assertEquals("http://127.0.0.1:19090/callback",
            validator.validate("http://127.0.0.1:19090/callback", "127.0.0.1").toString());
    }

    @Test
    void rejectsPrivateReservedAndMulticastAddressesByDefault() throws Exception {
        EpayUrlValidator validator = new EpayUrlValidator(new PaymentProperties());

        assertThrows(EpayException.class,
            () -> validator.validateResolvedAddress(InetAddress.getByName("127.0.0.1")));
        assertThrows(EpayException.class,
            () -> validator.validateResolvedAddress(InetAddress.getByName("10.1.2.3")));
        assertThrows(EpayException.class,
            () -> validator.validateResolvedAddress(InetAddress.getByName("169.254.1.1")));
        assertThrows(EpayException.class,
            () -> validator.validateResolvedAddress(InetAddress.getByName("224.0.0.1")));
        assertThrows(EpayException.class,
            () -> validator.validateResolvedAddress(InetAddress.getByName("fc00::1")));
    }
}
