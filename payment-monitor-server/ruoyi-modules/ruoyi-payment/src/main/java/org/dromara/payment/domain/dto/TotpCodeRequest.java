package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TotpCodeRequest {
    @NotBlank
    @Size(min = 6, max = 32)
    private String code;
}
