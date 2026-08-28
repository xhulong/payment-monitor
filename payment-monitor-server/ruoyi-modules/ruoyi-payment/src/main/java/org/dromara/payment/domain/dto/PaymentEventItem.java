package org.dromara.payment.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/**
 * 单条支付事件。
 */
@Data
public class PaymentEventItem {
    @NotBlank
    @Size(max = 64)
    private String clientEventId;
    @PositiveOrZero
    private Long deviceSequence;
    @NotBlank
    @Pattern(regexp = "WECHAT|ALIPAY")
    private String platform;
    @NotBlank
    @Pattern(regexp = "INCOME|EXPENSE|UNKNOWN")
    private String direction;
    private Long amountMinor;
    @Pattern(regexp = "[A-Z]{3}")
    private String currency = "CNY";
    private OffsetDateTime eventTime;
    @PositiveOrZero
    private Long eventTimeMs;
    private OffsetDateTime clientReceivedAt;
    @PositiveOrZero
    private Long clientReceivedAtMs;
    @NotBlank
    @Pattern(regexp = "PARSED|AMOUNT_NOT_FOUND|AMBIGUOUS")
    private String parseStatus;
    @Size(max = 32)
    private String parserVersion;
    @Size(max = 255)
    private String matchedRule;
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "fingerprint 必须为 SHA-256 十六进制字符串")
    private String fingerprint;
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "notificationKeyHash 必须为 SHA-256 十六进制字符串")
    private String notificationKeyHash;
    @Pattern(regexp = "[0-9a-fA-F]{64}", message = "rawHash 必须为 SHA-256 十六进制字符串")
    private String rawHash;
    private JsonNode rawPayload;
}
