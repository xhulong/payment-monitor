package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

/**
 * 管理端配对会话状态。
 */
public record PairingStatusVo(
    Long pairingSessionId,
    String status,
    OffsetDateTime expiresAt,
    Long deviceId,
    String deviceName,
    OffsetDateTime pairedAt
) {
}
