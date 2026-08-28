package org.dromara.web.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MfaLoginVerifyRequest {
    @NotBlank
    @Pattern(regexp = "[A-Fa-f0-9]{32}")
    private String challengeToken;

    @NotBlank
    @Pattern(regexp = "(?:\\d{6}|[A-Fa-f0-9]{12})")
    private String code;
}
