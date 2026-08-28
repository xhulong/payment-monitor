package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AppReleaseUpdateRequest {
    @NotNull
    @Min(1)
    private Integer minSupportedVersionCode;
    private OffsetDateTime enforcementAt;
    @NotBlank
    @Pattern(regexp = "OPTIONAL|REQUIRED|SECURITY_BLOCK")
    private String updateMode;
    @Size(max = 4000)
    private String releaseNotes;
}
