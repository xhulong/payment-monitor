package org.dromara.payment.integration.epay.security;

import lombok.RequiredArgsConstructor;
import org.dromara.payment.config.PaymentProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class EpaySecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PaymentProperties properties;

    public EncryptedSecret encrypt(String plainText) {
        String keyId = properties.getEasyPay().getActiveKeyId();
        return new EncryptedSecret(keyId, encryptWithKey(plainText, keyId, activeKey()));
    }

    public String decrypt(String cipherText, String keyId) {
        String key;
        if (keyId != null && keyId.equals(properties.getEasyPay().getActiveKeyId())) {
            key = activeKey();
        } else if (keyId != null && keyId.equals(properties.getEasyPay().getPreviousKeyId())) {
            key = properties.getEasyPay().getPreviousKey();
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("易支付上一版本主密钥未配置");
            }
        } else {
            throw new IllegalStateException("易支付密钥使用了未知主密钥版本");
        }
        try {
            byte[] value = Base64.getDecoder().decode(cipherText);
            if (value.length < 29) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(key), new GCMParameterSpec(128, iv));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("易支付接入密钥解密失败", exception);
        }
    }

    private String encryptWithKey(String plainText, String keyId, String key) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(key), new GCMParameterSpec(128, iv));
            cipher.updateAAD(keyId.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("易支付接入密钥加密失败", exception);
        }
    }

    private String activeKey() {
        String value = properties.getEasyPay().getActiveKey();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PAYMENT_INTEGRATION_MASTER_KEY 未配置");
        }
        if (properties.getEasyPay().getActiveKeyId() == null
            || properties.getEasyPay().getActiveKeyId().isBlank()) {
            throw new IllegalStateException("PAYMENT_INTEGRATION_ACTIVE_KEY_ID 未配置");
        }
        return value;
    }

    private SecretKeySpec deriveKey(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    public record EncryptedSecret(String keyId, String cipherText) {
    }
}
