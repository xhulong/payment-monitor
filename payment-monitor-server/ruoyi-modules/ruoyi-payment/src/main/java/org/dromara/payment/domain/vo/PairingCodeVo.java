package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 配对码结果。
 */
@Data
@AllArgsConstructor
public class PairingCodeVo {
    private Long pairingSessionId;
    private String pairingCode;
    private OffsetDateTime expiresAt;
    private String serverUrl;
    private Integer qrSchema;
    private Integer protocolVersion;
}
