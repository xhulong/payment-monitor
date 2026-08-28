package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

public record MailOutboxVo(
    Long id,
    String messageId,
    String messageType,
    String maskedRecipient,
    String subject,
    String status,
    Integer attemptCount,
    Integer maxAttempts,
    OffsetDateTime nextAttemptAt,
    OffsetDateTime expiresAt,
    OffsetDateTime sentAt,
    String lastError,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean retryable
) {
}
