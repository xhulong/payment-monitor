package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

public record MailSettingsVo(
    boolean enabled,
    String host,
    int port,
    boolean authEnabled,
    String username,
    boolean passwordConfigured,
    String fromName,
    String fromAddress,
    String securityMode,
    long connectionTimeoutMs,
    long readTimeoutMs,
    String source,
    OffsetDateTime updatedAt
) {
}
