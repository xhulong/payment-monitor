package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailChangeCodeRequest {
    @NotBlank
    @Email
    @Size(max = 100)
    private String newEmail;
    @NotBlank
    @Size(max = 64, message = "当前密码长度不能超过64位")
    private String password;
}
