package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantApplicationSaveRequest {
    @NotBlank
    @Size(max = 120)
    private String merchantDisplayName;
    @NotBlank
    @Size(max = 80)
    private String applicantName;
    @Size(max = 32)
    private String phoneNumber;
    @NotBlank
    @Size(max = 80)
    private String countryRegion;
    @Size(max = 80)
    private String province;
    @Size(max = 80)
    private String city;
    @NotBlank
    @Size(max = 1000)
    private String paymentUseCase;
    @NotBlank
    @Size(max = 64)
    private String monthlyOrderRange;
    @NotBlank
    @Size(max = 64)
    private String monthlyAmountRange;
    @NotBlank
    @Pattern(regexp = "WECHAT|ALIPAY|WECHAT,ALIPAY|ALIPAY,WECHAT")
    private String plannedPlatforms;
    @NotBlank
    private String agreementVersion;
    @NotBlank
    private String privacyVersion;
}
