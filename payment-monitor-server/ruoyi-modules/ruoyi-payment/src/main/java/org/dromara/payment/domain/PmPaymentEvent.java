package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 支付通知事件。
 */
@Data
@TableName(value = "pm_payment_event", autoResultMap = true)
public class PmPaymentEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private Long deviceId;
    private Long deviceSequence;
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
    @TableField(typeHandler = JsonbStringTypeHandler.class)
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
}
