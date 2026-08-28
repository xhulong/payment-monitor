package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantApiKeyCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String keyName;
}
