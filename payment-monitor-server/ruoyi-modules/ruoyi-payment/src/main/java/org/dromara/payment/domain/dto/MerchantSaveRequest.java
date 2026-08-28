package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantSaveRequest {
    @NotBlank
    @Pattern(regexp = "[A-Z0-9_-]{2,64}")
    private String merchantCode;
    @NotBlank
    @Size(max = 100)
    private String name;
    @Pattern(regexp = "[01]")
    private String status = "0";
    @NotBlank
    @Size(max = 64)
    private String timezone = "Asia/Shanghai";
    @Size(max = 500)
    private String remark;
    private Long adminUserId;
    @Size(max = 30)
    private String adminUserName;
    @Size(max = 30)
    private String adminNickName;
    @Size(min = 6, max = 64)
    private String adminPassword;
}
