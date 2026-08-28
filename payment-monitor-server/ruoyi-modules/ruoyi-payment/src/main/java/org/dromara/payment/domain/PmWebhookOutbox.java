package org.dromara.payment.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_webhook_outbox", autoResultMap = true)
public class PmWebhookOutbox implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long id;
    private String deliveryId;
    private String eventId;
    private Integer schemaVersion;
    private Long merchantId;
    private Long endpointId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String payload;
    private String status;
    private Integer attemptCount;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime lockedAt;
    private OffsetDateTime deliveredAt;
    private Integer lastHttpStatus;
    private String lastError;
    private String replayOfDeliveryId;
    private String replayReason;
    private String resolutionStatus;
    private Long resolvedBy;
    private OffsetDateTime resolvedAt;
    private String resolutionNote;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
