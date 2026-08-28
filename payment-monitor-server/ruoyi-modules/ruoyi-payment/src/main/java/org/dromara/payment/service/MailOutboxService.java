package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.MailOutboxPayload;
import org.dromara.payment.domain.PmMailOutbox;
import org.dromara.payment.mapper.MailOutboxMapper;
import org.dromara.payment.security.MailOutboxCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailOutboxService {

    private final MailOutboxMapper mapper;
    private final MailOutboxCipher cipher;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public PmMailOutbox enqueueText(
        String messageType,
        String recipient,
        String subject,
        String content,
        String deduplicationKey,
        OffsetDateTime expiresAt
    ) {
        return enqueue(
            messageType,
            recipient,
            subject,
            content,
            false,
            deduplicationKey,
            expiresAt
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public PmMailOutbox enqueueHtml(
        String messageType,
        String recipient,
        String subject,
        String content,
        String deduplicationKey,
        OffsetDateTime expiresAt
    ) {
        return enqueue(
            messageType,
            recipient,
            subject,
            content,
            true,
            deduplicationKey,
            expiresAt
        );
    }

    private PmMailOutbox enqueue(
        String messageType,
        String recipient,
        String subject,
        String content,
        boolean html,
        String deduplicationKey,
        OffsetDateTime expiresAt
    ) {
        validate(messageType, recipient, subject, content, expiresAt);
        OffsetDateTime now = now();
        String messageId = UUID.randomUUID().toString().replace("-", "");
        MailOutboxPayload payload = new MailOutboxPayload(
            recipient.trim().toLowerCase(Locale.ROOT),
            subject.trim(),
            content,
            html
        );
        MailOutboxCipher.EncryptedPayload encrypted = cipher.encrypt(
            messageId,
            objectMapper.writeValueAsString(payload)
        );

        PmMailOutbox outbox = new PmMailOutbox();
        outbox.setId(IdWorker.getId());
        outbox.setMessageId(messageId);
        outbox.setMessageType(normalize(messageType, 64));
        outbox.setDeduplicationKey(
            blankToNull(normalize(deduplicationKey, 128))
        );
        outbox.setPayloadCiphertext(encrypted.ciphertext());
        outbox.setEncryptionKeyId(encrypted.keyId());
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setMaxAttempts(properties.getMailOutbox().getMaxAttempts());
        outbox.setNextAttemptAt(now);
        outbox.setExpiresAt(expiresAt);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        mapper.insert(outbox);
        return outbox;
    }

    private void validate(
        String messageType,
        String recipient,
        String subject,
        String content,
        OffsetDateTime expiresAt
    ) {
        if (messageType == null
            || messageType.isBlank()
            || recipient == null
            || recipient.isBlank()
            || subject == null
            || subject.isBlank()
            || content == null) {
            throw new ServiceException("邮件 Outbox 任务内容不完整");
        }
        int maxAttempts = properties.getMailOutbox().getMaxAttempts();
        if (maxAttempts < 1 || maxAttempts > 20) {
            throw new ServiceException("邮件 Outbox 最大重试次数配置无效");
        }
        if (expiresAt != null && !expiresAt.isAfter(now())) {
            throw new ServiceException("邮件 Outbox 任务已过期");
        }
    }

    private String normalize(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maximum
            ? normalized
            : normalized.substring(0, maximum);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
