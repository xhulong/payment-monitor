package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WebhookEndpointSaveRequest {
    private Long merchantId;
    @NotBlank
    @Size(max = 100)
    private String endpointName;
    @NotBlank
    @Size(max = 1000)
    private String endpointUrl;
    @NotNull
    @Pattern(regexp = "0|1")
    private String status = "0";
    @Size(min = 1, max = 12)
    private List<@Pattern(regexp = "payment\\.(order\\.(paid|expired|cancelled|conflict|confirmed|reconciled|confirmation_revoked)|transaction\\.observed)") String>
        eventTypes = List.of("payment.order.paid");
    @NotNull
    @Pattern(regexp = "ALL|WECHAT|ALIPAY")
    private String platformFilter = "ALL";
}
