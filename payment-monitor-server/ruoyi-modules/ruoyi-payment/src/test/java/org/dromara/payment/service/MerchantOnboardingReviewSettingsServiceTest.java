package org.dromara.payment.service;

import org.dromara.system.domain.bo.SysConfigBo;
import org.dromara.system.service.ISysConfigService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantOnboardingReviewSettingsServiceTest {

    @Test
    void defaultsToReviewEnabledForMissingOrInvalidValues() {
        ISysConfigService configService = mock(ISysConfigService.class);
        MerchantOnboardingReviewSettingsService service =
            new MerchantOnboardingReviewSettingsService(configService);

        when(configService.selectConfigByKey(
            MerchantOnboardingReviewSettingsService.REVIEW_ENABLED_CONFIG_KEY
        )).thenReturn("", "invalid-value", "TRUE");

        assertTrue(service.reviewEnabled());
        assertTrue(service.reviewEnabled());
        assertTrue(service.reviewEnabled());
    }

    @Test
    void readsAndUpdatesExplicitBooleanValue() {
        ISysConfigService configService = mock(ISysConfigService.class);
        MerchantOnboardingReviewSettingsService service =
            new MerchantOnboardingReviewSettingsService(configService);
        when(configService.selectConfigByKey(
            MerchantOnboardingReviewSettingsService.REVIEW_ENABLED_CONFIG_KEY
        )).thenReturn("false");

        assertFalse(service.reviewEnabled());
        assertFalse(service.update(false).reviewEnabled());

        ArgumentCaptor<SysConfigBo> configCaptor =
            ArgumentCaptor.forClass(SysConfigBo.class);
        verify(configService).updateConfig(configCaptor.capture());
        assertEquals(
            MerchantOnboardingReviewSettingsService.REVIEW_ENABLED_CONFIG_KEY,
            configCaptor.getValue().getConfigKey()
        );
        assertEquals("false", configCaptor.getValue().getConfigValue());
    }
}
