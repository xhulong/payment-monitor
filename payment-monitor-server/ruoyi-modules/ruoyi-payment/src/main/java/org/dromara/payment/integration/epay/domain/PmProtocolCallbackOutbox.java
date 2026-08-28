package org.dromara.payment.integration.epay.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.payment.mybatis.JsonbStringTypeHandler;

import java.time.OffsetDateTime;

@Data
@TableName(value = "pm_protocol_callback_outbox", autoResultMap = true)
public class PmProtocolCallbackOutbox {
    @TableId
    private Long id;
    private String deliveryId;
    private String eventId;
    private Long merchantId;
    private Long integrationId;
    private Long bindingId;
    private String callbackKind;
    private String targetUrl;
    private String requestMethod;
    private String contentType;
    private Integer credentialVersion;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String unsignedParams;
    private String status;
    private Integer attemptCount;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime lockedAt;
    private OffsetDateTime deliveredAt;
    private Integer lastHttpStatus;
    private String lastResponse;
    private String lastError;
    private Boolean strictAcknowledged;
    private Long replayOfId;
    private String replayReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
