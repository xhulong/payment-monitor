package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QrAssetSaveRequest {
    private Long merchantId;
    @Pattern(
        regexp = "(?:\\s*|[A-Za-z0-9_-]{2,64})",
        message = "资产编码可留空自动生成；手动填写仅支持 2-64 位字母、数字、下划线和中划线"
    )
    private String assetCode;
    @NotBlank
    @Pattern(regexp = "WECHAT|ALIPAY")
    private String platform;
    @NotBlank
    @Size(max = 100)
    private String assetName;
    @NotBlank
    @Size(max = 4096)
    private String qrContentTemplate;
    @Pattern(regexp = "0|1")
    private String status = "0";
    @Size(max = 500)
    private String remark;
}
