package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备配对请求。
 */
@Data
public class PairDeviceRequest {
    private int protocolVersion = 1;
    private Long previousDeviceId;
    @NotBlank
    @Pattern(regexp = "\\d{8}", message = "配对码必须为 8 位数字")
    private String pairingCode;
    @NotBlank
    @Size(max = 100)
    private String deviceName;
    @Size(max = 128)
    private String androidIdHash;
    @Size(max = 32)
    private String appVersion;
    @NotNull(message = "当前 App 版本过旧，缺少 appVersionCode，请安装最新版后重新配对")
    @Min(1)
    private Integer appVersionCode;
    @Size(max = 32)
    private String parserVersion;
}
