package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.domain.MailOutboxPayload;
import org.dromara.payment.domain.PmMailOutbox;
import org.dromara.payment.mapper.MailOutboxMapper;
import org.dromara.payment.security.MailOutboxCipher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailOutboxWorker {

    private final MailOutboxMapper mapper;
    private final MailOutboxCipher cipher;
    private final MailDeliveryClient deliveryClient;
    private final PaymentProperties properties;
    private final MailSettingsService mailSettingsService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${payment.mail-outbox.worker-delay-ms:1000}")
    public void processDue() {
        if (!properties.getMailOutbox().isEnabled()
            || !mailSettingsService.enabled()) {
            return;
        }
        OffsetDateTime now = now();
        List<PmMailOutbox> jobs = mapper.claimDue(
            now,
            now.minusSeconds(
                properties.getMailOutbox().getLockTimeoutSeconds()
            ),
            properties.getMailOutbox().getMaxBatchSize()
        );
        for (PmMailOutbox outbox : jobs) {
            try {
                deliver(outbox);
            } catch (RuntimeException e) {
                log.error(
                    "Mail Outbox persistence failed, messageId={}",
                    outbox.getMessageId(),
                    e
                );
            }
        }
    }

    void deliver(PmMailOutbox outbox) {
        OffsetDateTime timestamp = now();
        if (isExpired(outbox, timestamp)) {
            cancelExpired(outbox, timestamp);
            return;
        }

        int attempt = outbox.getAttemptCount() + 1;
        boolean success = false;
        boolean permanentFailure = false;
        String error = null;
        try {
            String plaintext = cipher.decrypt(
                outbox.getMessageId(),
                outbox.getEncryptionKeyId(),
                outbox.getPayloadCiphertext()
            );
            MailOutboxPayload payload = objectMapper.readValue(
                plaintext,
                MailOutboxPayload.class
            );
            validatePayload(payload);
            deliveryClient.send(payload);
            success = true;
        } catch (ServiceException | IllegalArgumentException e) {
            permanentFailure = true;
            error = e.getClass().getSimpleName();
        } catch (Exception e) {
            error = e.getClass().getSimpleName();
        }
        update(
            outbox,
            attempt,
            timestamp,
            success,
            permanentFailure,
            error
        );
    }

    private void update(
        PmMailOutbox outbox,
        int attempt,
        OffsetDateTime timestamp,
        boolean success,
        boolean permanentFailure,
        String error
    ) {
        outbox.setAttemptCount(attempt);
        outbox.setLockedAt(null);
        outbox.setUpdatedAt(timestamp);
        if (success) {
            outbox.setStatus("SENT");
            outbox.setSentAt(timestamp);
            outbox.setLastError(null);
        } else {
            OffsetDateTime nextAttempt = timestamp.plusSeconds(
                retryDelaySeconds(attempt)
            );
            boolean retryable = !permanentFailure
                && attempt < outbox.getMaxAttempts()
                && (outbox.getExpiresAt() == null
                    || nextAttempt.isBefore(outbox.getExpiresAt()));
            if (retryable) {
                outbox.setStatus("RETRYING");
                outbox.setNextAttemptAt(nextAttempt);
                outbox.setLastError(truncate(error, 500));
            } else {
                outbox.setStatus("DEAD");
                outbox.setLastError(
                    truncate(
                        isExpired(outbox, nextAttempt)
                            ? "DELIVERY_WINDOW_EXPIRED"
                            : error,
                        500
                    )
                );
            }
        }
        mapper.updateById(outbox);
    }

    private void cancelExpired(
        PmMailOutbox outbox,
        OffsetDateTime timestamp
    ) {
        outbox.setStatus("CANCELLED");
        outbox.setLockedAt(null);
        outbox.setLastError("EXPIRED_BEFORE_DELIVERY");
        outbox.setUpdatedAt(timestamp);
        mapper.updateById(outbox);
    }

    private void validatePayload(MailOutboxPayload payload) {
        if (payload == null
            || payload.recipient() == null
            || payload.recipient().isBlank()
            || payload.subject() == null
            || payload.subject().isBlank()
            || payload.content() == null) {
            throw new IllegalArgumentException(
                "Mail Outbox payload is incomplete"
            );
        }
    }

    private long retryDelaySeconds(int attempt) {
        PaymentProperties.MailOutbox config = properties.getMailOutbox();
        long multiplier = 1L << Math.min(
            10,
            Math.max(0, attempt - 1)
        );
        long delay;
        try {
            delay = Math.multiplyExact(
                config.getInitialRetrySeconds(),
                multiplier
            );
        } catch (ArithmeticException e) {
            delay = config.getMaxRetrySeconds();
        }
        return Math.max(
            1,
            Math.min(config.getMaxRetrySeconds(), delay)
        );
    }

    private boolean isExpired(
        PmMailOutbox outbox,
        OffsetDateTime timestamp
    ) {
        return outbox.getExpiresAt() != null
            && !outbox.getExpiresAt().isAfter(timestamp);
    }

    private String truncate(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
