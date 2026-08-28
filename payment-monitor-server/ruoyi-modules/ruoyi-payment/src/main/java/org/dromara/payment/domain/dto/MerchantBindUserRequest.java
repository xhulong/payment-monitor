package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MerchantBindUserRequest {
    @NotNull
    private Long userId;
}
