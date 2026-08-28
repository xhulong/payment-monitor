package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmPaymentEventReview;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = PmPaymentEventReview.class)
public class PaymentEventReviewVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
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
