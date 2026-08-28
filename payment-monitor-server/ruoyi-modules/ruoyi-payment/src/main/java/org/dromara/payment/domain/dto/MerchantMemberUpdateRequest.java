package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MerchantMemberUpdateRequest {
    @Pattern(regexp = "OWNER|ADMIN|FINANCE|DEVELOPER|VIEWER")
    private String roleCode;
    @Pattern(regexp = "[01]")
    private String status;
}
