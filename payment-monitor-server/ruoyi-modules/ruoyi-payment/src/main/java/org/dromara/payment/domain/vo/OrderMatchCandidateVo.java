package org.dromara.payment.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class OrderMatchCandidateVo {
    private Long id;
    private String clientEventId;
    private String platform;
    private Long amountMinor;
    private String currency;
    private OffsetDateTime eventTime;
    private OffsetDateTime receivedAt;
    private String status;
    private String duplicateStatus;
    private boolean exactMatch;
}
