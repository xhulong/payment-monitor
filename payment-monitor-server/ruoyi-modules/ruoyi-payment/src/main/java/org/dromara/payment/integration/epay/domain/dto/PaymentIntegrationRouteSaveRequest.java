package org.dromara.payment.integration.epay.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PaymentIntegrationRouteSaveRequest {
    @NotNull @Size(max = 20)
    private List<@Valid Item> routes;

    @Data
    public static class Item {
        @NotNull @Pattern(regexp = "alipay|wxpay")
        private String payType;
        @NotNull @Pattern(regexp = "ALIPAY|WECHAT")
        private String platform;
        @NotNull
        private Long qrAssetId;
        @Min(1) @Max(9999)
        private Integer priority = 100;
        @Pattern(regexp = "0|1")
        private String status = "0";
    }
}
