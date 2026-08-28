package org.dromara.payment.integration.epay.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PaymentIntegrationSaveRequest {
    private Long merchantId;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,64}")
    private String integrationCode;
    @NotBlank @Size(max = 100)
    private String integrationName;
    @Min(30) @Max(3600)
    private Integer defaultExpireSeconds = 300;
    @NotBlank @Pattern(regexp = "GET|POST")
    private String notifyMethod = "GET";
    @NotBlank @Pattern(regexp = "NOTIFICATION_MATCHED|MANUAL_CONFIRMED|RECONCILED")
    private String callbackPolicy = "NOTIFICATION_MATCHED";
    @NotEmpty @Size(max = 20)
    private List<@Size(max = 253) String> allowedCallbackHosts;
    @Size(max = 500)
    private String remark;
}
