package org.dromara.payment.domain.vo;

import org.dromara.payment.domain.PmMerchantApplication;

import java.util.List;

public record MerchantOnboardingStatusVo(
    boolean onboardingAvailable,
    boolean reviewEnabled,
    String verifiedEmail,
    PmMerchantApplication application,
    Long merchantId,
    String merchantCode,
    String merchantName,
    String merchantLifecycle,
    String memberRole,
    boolean mfaEnabled,
    List<ChecklistItem> checklist
) {
    public record ChecklistItem(String code, String label, boolean completed, boolean required) {
    }
}
