package org.dromara.payment.service;

import lombok.RequiredArgsConstructor;
import org.dromara.payment.domain.vo.MerchantApplicationReviewSettingsVo;
import org.dromara.system.domain.bo.SysConfigBo;
import org.dromara.system.service.ISysConfigService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantOnboardingReviewSettingsService {
    public static final String REVIEW_ENABLED_CONFIG_KEY =
        "payment.merchant.onboarding.reviewEnabled";

    private final ISysConfigService configService;

    public boolean reviewEnabled() {
        String value = configService.selectConfigByKey(REVIEW_ENABLED_CONFIG_KEY);
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return true;
    }

    public MerchantApplicationReviewSettingsVo view() {
        return new MerchantApplicationReviewSettingsVo(reviewEnabled());
    }

    public MerchantApplicationReviewSettingsVo update(boolean reviewEnabled) {
        SysConfigBo config = new SysConfigBo();
        config.setConfigKey(REVIEW_ENABLED_CONFIG_KEY);
        config.setConfigValue(Boolean.toString(reviewEnabled));
        configService.updateConfig(config);
        return new MerchantApplicationReviewSettingsVo(reviewEnabled);
    }
}
