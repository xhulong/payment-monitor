package org.dromara.payment.security;

import org.dromara.payment.config.PaymentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("dev")
class DeviceSecretCipherTest {

    @Test
    void encryptsAndDecryptsDeviceSecretWithRandomIv() {
        PaymentProperties properties = new PaymentProperties();
        properties.getSecurity().setMasterKey("local-test-master-key-with-more-than-32-characters");
        DeviceSecretCipher cipher = new DeviceSecretCipher(properties);

        String first = cipher.encrypt("device-secret");
        String second = cipher.encrypt("device-secret");

        assertNotEquals(first, second);
        assertEquals("device-secret", cipher.decrypt(first));
        assertEquals("device-secret", cipher.decrypt(second));
    }

    @Test
    void refusesToOperateWithoutMasterKey() {
        DeviceSecretCipher cipher = new DeviceSecretCipher(new PaymentProperties());

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("device-secret"));
    }
}
