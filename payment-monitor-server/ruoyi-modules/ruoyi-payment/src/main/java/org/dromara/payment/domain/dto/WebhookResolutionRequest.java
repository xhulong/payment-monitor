package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WebhookResolutionRequest {
    @NotBlank
    @Pattern(regexp = "RESOLVED|IGNORED")
    private String status;
    @NotBlank
    @Size(max = 1000)
    private String note;
}
