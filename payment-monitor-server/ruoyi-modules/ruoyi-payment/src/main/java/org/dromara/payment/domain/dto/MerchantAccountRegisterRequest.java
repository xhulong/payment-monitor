package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantAccountRegisterRequest {
    @NotBlank
    @Size(min = 4, max = 30)
    @Pattern(
        regexp = "^[A-Za-z][A-Za-z0-9_]{3,29}$",
        message = "用户名必须以字母开头，只能包含字母、数字和下划线"
    )
    private String username;
    @Email
    @NotBlank
    @Size(max = 50)
    private String email;
    @NotBlank
    @Size(max = 30)
    private String nickname;
    @NotBlank
    @Size(min = 12, max = 64)
    private String password;
    @NotBlank
    private String emailCode;
    private String invitationToken;
}
