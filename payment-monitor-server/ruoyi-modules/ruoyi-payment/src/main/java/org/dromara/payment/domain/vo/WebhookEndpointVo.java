package org.dromara.payment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Data
public class WebhookEndpointVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long merchantId;
    private String merchantCode;
    private String merchantName;
    private String endpointName;
    private String endpointUrl;
    private String status;
    private List<String> eventTypes;
    private String platformFilter;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
