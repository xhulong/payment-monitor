package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MerchantInvitationCreateRequest {
    private Long merchantId;
    @Email
    @NotBlank
    private String email;
    @NotBlank
    @Pattern(regexp = "OWNER|ADMIN|FINANCE|DEVELOPER|VIEWER")
    private String roleCode;
}
