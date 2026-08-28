package org.dromara.payment.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class WebhookOutboxQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long merchantId;
    private String deliveryId;
    private String status;
    private String eventType;
    private Long aggregateId;
    private Long endpointId;
    private String resolutionStatus;
}
