package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransactionReverseRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;
}
