package org.dromara.payment.integration.epay.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("pm_protocol_callback_delivery_log")
public class PmProtocolCallbackDeliveryLog {
    @TableId
    private Long id;
    private Long outboxId;
    private String deliveryId;
    private Integer attemptNumber;
    private OffsetDateTime requestAt;
    private OffsetDateTime responseAt;
    private Long durationMs;
    private Integer httpStatus;
    private String responseExcerpt;
    private String errorMessage;
    private Boolean acknowledged;
    private OffsetDateTime createdAt;
}
