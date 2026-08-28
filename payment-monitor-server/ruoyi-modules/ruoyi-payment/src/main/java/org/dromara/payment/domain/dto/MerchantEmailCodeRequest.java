package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MerchantEmailCodeRequest {
    @Email
    @NotBlank
    private String email;
    @NotBlank
    private String captchaUuid;
    @NotBlank
    private String captchaCode;
}
