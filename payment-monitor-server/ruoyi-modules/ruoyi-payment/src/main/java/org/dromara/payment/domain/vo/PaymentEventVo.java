package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmPaymentEvent;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 支付事件视图。
 */
@Data
@AutoMapper(target = PmPaymentEvent.class)
public class PaymentEventVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private Long deviceId;
    private String clientEventId;
    private String platform;
    private String direction;
    private Long amountMinor;
    private String currency;
    private OffsetDateTime eventTime;
    private Long eventTimeMs;
    private OffsetDateTime clientReceivedAt;
    private Long clientReceivedAtMs;
    private OffsetDateTime clientSentAt;
    private Long clientSentAtMs;
    private OffsetDateTime receivedAt;
    private String parseStatus;
    private String parserVersion;
    private String matchedRule;
    private String fingerprint;
    private String notificationKeyHash;
    private String rawHash;
    private String rawPayload;
    private String status;
    private OffsetDateTime reviewedAt;
    private Long reviewedBy;
    private String reviewNote;
    private String duplicateStatus;
    private Long duplicateOfEventId;
    private OffsetDateTime duplicateDetectedAt;
    private OffsetDateTime duplicateReviewedAt;
    private Long duplicateReviewedBy;
    private String duplicateReviewNote;
    private List<PaymentEventReviewVo> reviewHistory;
}
