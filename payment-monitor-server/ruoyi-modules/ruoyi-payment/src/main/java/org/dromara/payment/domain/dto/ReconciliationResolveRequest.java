package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReconciliationResolveRequest {
    @NotBlank
    @Pattern(regexp = "RESOLVE|IGNORE|RECONCILE")
    private String action;
    @Size(max = 500)
    private String note;
}
