package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantOrderCreateRequest {
    @NotBlank
    @Size(max = 64)
    private String merchantOrderNo;
    @NotBlank
    @Pattern(regexp = "WECHAT|ALIPAY")
    private String platform;
    @NotBlank
    @Size(max = 64)
    private String qrAssetCode;
    @Min(1)
    private long amountMinor;
    @Min(30)
    @Max(3600)
    private Integer expiresSeconds = 300;
    @Size(max = 200)
    private String subject;
    @Size(max = 500)
    private String customerNote;
}
