package org.dromara.payment.security;

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
public class AccountMfaCipher {
    private final PaymentProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AccountMfaCipher(PaymentProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception exception) {
            throw new IllegalStateException("MFA 密钥加密失败", exception);
        }
    }

    public String decrypt(String value) {
        try {
            byte[] input = Base64.getDecoder().decode(value);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[input.length - iv.length];
            System.arraycopy(input, 0, iv, 0, iv.length);
            System.arraycopy(input, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("MFA 密钥解密失败", exception);
        }
    }

    private SecretKeySpec key() {
        try {
            String configured = properties.getAccountMfa().getMasterKey();
            if (configured == null || configured.isBlank()) {
                throw new IllegalStateException("ACCOUNT_MFA_MASTER_KEY 未配置");
            }
            return new SecretKeySpec(
                MessageDigest.getInstance("SHA-256")
                    .digest(configured.getBytes(StandardCharsets.UTF_8)),
                "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("MFA 主密钥无效", exception);
        }
    }
}
