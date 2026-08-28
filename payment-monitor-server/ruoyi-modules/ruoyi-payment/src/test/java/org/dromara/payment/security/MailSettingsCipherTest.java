package org.dromara.payment.security;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class MailSettingsCipherTest {

    @Test
    void encryptsPasswordAndSupportsPreviousKeyDuringRotation() {
        PaymentProperties properties = properties();
        MailSettingsCipher cipher = new MailSettingsCipher(properties);
        var encrypted = cipher.encrypt("smtp-authorization-code");

        assertNotEquals("smtp-authorization-code", encrypted.ciphertext());
        assertEquals(
            "smtp-authorization-code",
            cipher.decrypt(encrypted.keyId(), encrypted.ciphertext())
        );

        properties.getMailSettings().setPreviousKeyId(encrypted.keyId());
        properties.getMailSettings().setPreviousKey(
            properties.getMailSettings().getActiveKey()
        );
        properties.getMailSettings().setActiveKeyId("mail-settings-v2");
        properties.getMailSettings().setActiveKey(
            "abcdef0123456789abcdef0123456789"
        );

        assertEquals(
            "smtp-authorization-code",
            cipher.decrypt(encrypted.keyId(), encrypted.ciphertext())
        );
    }

    @Test
    void rejectsUnknownKeyVersionAndShortMasterKey() {
        PaymentProperties properties = properties();
        MailSettingsCipher cipher = new MailSettingsCipher(properties);

        assertThrows(
            ServiceException.class,
            () -> cipher.decrypt("unknown-key", "invalid")
        );

        properties.getMailSettings().setActiveKey("too-short");
        assertThrows(
            ServiceException.class,
            () -> cipher.encrypt("secret")
        );
    }

    private PaymentProperties properties() {
        PaymentProperties properties = new PaymentProperties();
        properties.getMailSettings().setActiveKeyId("mail-settings-v1");
        properties.getMailSettings().setActiveKey(
            "0123456789abcdef0123456789abcdef"
        );
        return properties;
    }
}
