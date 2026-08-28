package org.dromara.payment.integration.epay.domain.vo;

import lombok.Data;
import org.dromara.payment.integration.epay.domain.PmProtocolCallbackDeliveryLog;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ProtocolCallbackVo {
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String deliveryId;
    private String eventId;
    private Long integrationId;
    private String integrationName;
    private Long bindingId;
    private String externalOrderNo;
    private String gatewayTradeNo;
    private String requestMethod;
    private String targetUrl;
    private String status;
    private Integer attemptCount;
    private OffsetDateTime nextAttemptAt;
    private OffsetDateTime deliveredAt;
    private Integer lastHttpStatus;
    private String lastResponse;
    private String lastError;
    private Boolean strictAcknowledged;
    private Long replayOfId;
    private String replayReason;
    private OffsetDateTime createdAt;
    private List<PmProtocolCallbackDeliveryLog> deliveryLogs;
}
