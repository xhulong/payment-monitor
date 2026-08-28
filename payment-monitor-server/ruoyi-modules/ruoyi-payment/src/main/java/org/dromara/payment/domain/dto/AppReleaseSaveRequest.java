package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AppReleaseSaveRequest {
    @Min(1)
    private Integer versionCode;
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[0-9A-Za-z._-]{1,64}")
    private String versionName;
    @Min(1)
    private Integer minSupportedVersionCode;
    private OffsetDateTime enforcementAt;
    @Pattern(regexp = "OPTIONAL|REQUIRED|SECURITY_BLOCK")
    private String updateMode = "REQUIRED";
    @Size(max = 4000)
    private String releaseNotes;
}
