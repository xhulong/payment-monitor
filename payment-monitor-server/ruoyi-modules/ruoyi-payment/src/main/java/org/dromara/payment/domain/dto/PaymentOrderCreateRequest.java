package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentOrderCreateRequest {
    private Long merchantId;
    @Size(max = 64)
    private String merchantOrderNo;
    @NotBlank
    @Pattern(regexp = "WECHAT|ALIPAY")
    private String platform;
    @NotNull
    private Long qrAssetId;
    @NotNull
    @Min(1)
    @Max(99999999)
    private Long amountMinor;
    @Min(60)
    @Max(3600)
    private Integer expiresSeconds = 300;
    @Size(max = 200)
    private String subject;
    @Size(max = 500)
    private String customerNote;
}
