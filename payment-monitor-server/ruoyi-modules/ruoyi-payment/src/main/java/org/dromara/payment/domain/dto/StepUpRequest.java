package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StepUpRequest {
    @NotBlank
    @Size(min = 6, max = 32)
    private String code;
    @NotBlank
    @Pattern(regexp = "[A-Z0-9_.:-]{3,64}")
    private String operation;
}
