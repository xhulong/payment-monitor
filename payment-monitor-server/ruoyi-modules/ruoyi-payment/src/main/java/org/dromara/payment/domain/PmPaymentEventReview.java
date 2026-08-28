package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Immutable audit record for manual payment-event review.
 */
@Data
@TableName("pm_payment_event_review")
public class PmPaymentEventReview implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private Long merchantId;
    private Long eventId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String beforeDirection;
    private String afterDirection;
    private Long beforeAmountMinor;
    private Long afterAmountMinor;
    private String note;
    private Long operatedBy;
    private OffsetDateTime operatedAt;
}
