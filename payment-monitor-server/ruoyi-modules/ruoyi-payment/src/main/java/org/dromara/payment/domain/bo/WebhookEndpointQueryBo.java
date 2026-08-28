package org.dromara.payment.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class WebhookEndpointQueryBo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long merchantId;
    private String endpointName;
    private String status;
}
