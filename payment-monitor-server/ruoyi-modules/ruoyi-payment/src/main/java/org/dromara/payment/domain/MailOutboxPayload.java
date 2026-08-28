package org.dromara.payment.domain;

public record MailOutboxPayload(
    String recipient,
    String subject,
    String content,
    boolean html
) {
}
