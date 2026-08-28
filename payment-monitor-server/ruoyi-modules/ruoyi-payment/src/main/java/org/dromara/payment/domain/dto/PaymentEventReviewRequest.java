package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentEventReviewRequest {
    @NotBlank
    @Pattern(regexp = "REVIEW|CORRECT|IGNORE")
    private String action;
    @Pattern(regexp = "INCOME|EXPENSE|UNKNOWN")
    private String direction;
    @Min(1)
    private Long amountMinor;
    @Size(max = 500)
    private String note;
}
