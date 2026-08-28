package org.dromara.payment.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebhookEndpointSecretVo {
    private WebhookEndpointVo endpoint;
    private String webhookSecret;
}
