package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmWebhookDeliveryLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AutoMapper(target = PmWebhookDeliveryLog.class)
public class WebhookDeliveryLogVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String deliveryId;
    private Integer attemptNumber;
    private OffsetDateTime requestAt;
    private OffsetDateTime responseAt;
    private Long durationMs;
    private Integer httpStatus;
    private String responseExcerpt;
    private String errorMessage;
    private Boolean success;
}
