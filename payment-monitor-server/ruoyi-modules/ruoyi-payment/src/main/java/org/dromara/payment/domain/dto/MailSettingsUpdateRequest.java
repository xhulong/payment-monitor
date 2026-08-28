package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MailSettingsUpdateRequest {
    @NotNull
    private Boolean enabled;
    @NotBlank
    @Size(max = 255)
    private String host;
    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;
    @NotNull
    private Boolean authEnabled;
    @Size(max = 255)
    private String username;
    @Size(max = 512)
    private String password;
    private Boolean clearPassword = false;
    @NotBlank
    @Size(max = 100)
    private String fromName;
    @NotBlank
    @Email
    @Size(max = 255)
    private String fromAddress;
    @NotBlank
    private String securityMode;
    @NotNull
    @Min(1000)
    @Max(120000)
    private Long connectionTimeoutMs;
    @NotNull
    @Min(1000)
    @Max(120000)
    private Long readTimeoutMs;
}
