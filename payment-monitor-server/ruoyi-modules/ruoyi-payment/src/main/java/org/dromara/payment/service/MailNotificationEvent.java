package org.dromara.payment.service;

import java.time.OffsetDateTime;

public record MailNotificationEvent(
    String messageType,
    String recipient,
    String subject,
    String html,
    String deduplicationKey,
    OffsetDateTime expiresAt
) {
}
