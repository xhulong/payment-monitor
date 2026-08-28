package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MerchantApplicationReviewSettingsUpdateRequest {
    @NotNull
    private Boolean reviewEnabled;
}
