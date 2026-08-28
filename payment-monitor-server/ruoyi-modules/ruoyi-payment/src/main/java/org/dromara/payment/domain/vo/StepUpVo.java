package org.dromara.payment.domain.vo;

import java.time.OffsetDateTime;

public record StepUpVo(String token, String operation, OffsetDateTime expiresAt) {
}
