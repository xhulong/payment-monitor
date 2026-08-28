package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName("pm_webhook_delivery_log")
public class PmWebhookDeliveryLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
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
    private Boolean success;
    private OffsetDateTime createdAt;
}
