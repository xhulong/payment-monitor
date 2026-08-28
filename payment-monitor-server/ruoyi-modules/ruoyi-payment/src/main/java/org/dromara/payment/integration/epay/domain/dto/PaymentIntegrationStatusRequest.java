package org.dromara.payment.integration.epay.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PaymentIntegrationStatusRequest {
    @NotBlank @Pattern(regexp = "0|1")
    private String status;
}
