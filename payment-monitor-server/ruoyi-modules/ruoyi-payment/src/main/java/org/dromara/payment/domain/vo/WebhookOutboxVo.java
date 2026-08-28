package org.dromara.payment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.payment.domain.PmWebhookOutbox;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AutoMapper(target = PmWebhookOutbox.class)
public class WebhookOutboxVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String deliveryId;
    private String eventId;
    private Long endpointId;
    private String endpointName;
    private String endpointUrl;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
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
    private List<WebhookDeliveryLogVo> deliveryLogs;
}
