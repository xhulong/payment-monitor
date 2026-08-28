package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DuplicateReviewRequest {
    @NotBlank
    @Pattern(regexp = "CONFIRMED|EXCLUDED")
    private String status;
    @Size(max = 500)
    private String note;
}
