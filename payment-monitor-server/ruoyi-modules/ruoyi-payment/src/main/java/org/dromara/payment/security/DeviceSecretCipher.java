package org.dromara.payment.security;

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

/**
 * 设备密钥 AES-GCM 加解密。
 */
@Component
@RequiredArgsConstructor
public class DeviceSecretCipher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final PaymentProperties properties;

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) {
            throw new IllegalStateException("设备密钥加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] value = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("设备密钥解密失败", e);
        }
    }

    private SecretKeySpec key() throws Exception {
        String masterKey = properties.getSecurity().getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("PAYMENT_MASTER_KEY 未配置");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
