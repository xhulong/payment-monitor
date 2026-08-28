package org.dromara.payment.domain.vo;

public record MerchantContextVo(
    boolean superAdmin,
    Long merchantId,
    String merchantCode,
    String merchantName,
    String accountType,
    String scopeMode,
    boolean canAccessAllMerchants,
    String displayTimezone
) {
}
