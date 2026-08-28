package org.dromara.payment.security;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
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
public class MailSettingsCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final PaymentProperties properties;

    public EncryptedValue encrypt(String plaintext) {
        PaymentProperties.MailSettings config = properties.getMailSettings();
        String keyId = require(
            config.getActiveKeyId(),
            "邮件设置活动密钥 ID 未配置"
        );
        return new EncryptedValue(
            keyId,
            encryptWithKey(plaintext, keyId, config.getActiveKey())
        );
    }

    public String decrypt(String keyId, String ciphertext) {
        PaymentProperties.MailSettings config = properties.getMailSettings();
        String configuredKey;
        if (keyId != null && keyId.equals(config.getActiveKeyId())) {
            configuredKey = config.getActiveKey();
        } else if (keyId != null && keyId.equals(config.getPreviousKeyId())) {
            configuredKey = config.getPreviousKey();
        } else {
            throw new ServiceException("邮件设置密钥版本不可用");
        }
        try {
            byte[] input = Base64.getDecoder().decode(ciphertext);
            if (input.length <= IV_BYTES) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(input);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(configuredKey),
                new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(aad(keyId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("邮件设置密码解密失败", exception);
        }
    }

    private String encryptWithKey(
        String plaintext,
        String keyId,
        String configuredKey
    ) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key(configuredKey),
                new GCMParameterSpec(TAG_BITS, iv)
            );
            cipher.updateAAD(aad(keyId));
            byte[] encrypted = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array()
            );
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("邮件设置密码加密失败", exception);
        }
    }

    private SecretKeySpec key(String configured) throws Exception {
        String value = require(configured, "邮件设置主密钥未配置");
        if (value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ServiceException("邮件设置主密钥至少需要 32 字节");
        }
        return new SecretKeySpec(
            MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)),
            "AES"
        );
    }

    private byte[] aad(String keyId) {
        return ("payment-monitor/mail-settings/v1\n1\n" + keyId)
            .getBytes(StandardCharsets.UTF_8);
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(message);
        }
        return value.trim();
    }

    public record EncryptedValue(String keyId, String ciphertext) {
    }
}
