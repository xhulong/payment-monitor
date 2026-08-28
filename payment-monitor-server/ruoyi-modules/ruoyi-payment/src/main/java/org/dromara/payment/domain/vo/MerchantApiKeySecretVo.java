package org.dromara.payment.domain.vo;

public record MerchantApiKeySecretVo(
    MerchantApiKeyVo apiKey,
    String apiSecret
) {
}
