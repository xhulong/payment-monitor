package org.dromara.payment.controller;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.domain.dto.MerchantApplicationReviewSettingsUpdateRequest;
import org.dromara.payment.service.AccountMfaService;
import org.dromara.payment.service.MerchantOnboardingReviewSettingsService;
import org.dromara.payment.service.MerchantOnboardingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("dev")
class MerchantApplicationReviewControllerTest {

    @Test
    void reviewSettingsUpdateRequiresOperationBoundMfaToken() {
        AccountMfaService mfaService = mock(AccountMfaService.class);
        MerchantOnboardingReviewSettingsService settingsService =
            mock(MerchantOnboardingReviewSettingsService.class);
        doThrow(new ServiceException("敏感操作需要 MFA 二次验证"))
            .when(mfaService)
            .requireStepUp(null, "MERCHANT_APPLICATION_REVIEW_SETTINGS");
        MerchantApplicationReviewController controller =
            new MerchantApplicationReviewController(
                mock(MerchantOnboardingService.class),
                mfaService,
                settingsService
            );
        MerchantApplicationReviewSettingsUpdateRequest request =
            new MerchantApplicationReviewSettingsUpdateRequest();
        request.setReviewEnabled(false);

        assertThrows(
            ServiceException.class,
            () -> controller.updateReviewSettings(request, null)
        );

        verify(settingsService, never()).update(false);
    }

    @Test
    void reviewSettingsUpdateExecutesAfterMfaVerification() {
        AccountMfaService mfaService = mock(AccountMfaService.class);
        MerchantOnboardingReviewSettingsService settingsService =
            mock(MerchantOnboardingReviewSettingsService.class);
        MerchantApplicationReviewController controller =
            new MerchantApplicationReviewController(
                mock(MerchantOnboardingService.class),
                mfaService,
                settingsService
            );
        MerchantApplicationReviewSettingsUpdateRequest request =
            new MerchantApplicationReviewSettingsUpdateRequest();
        request.setReviewEnabled(false);

        controller.updateReviewSettings(request, "step-up-token");

        verify(mfaService).requireStepUp(
            "step-up-token",
            "MERCHANT_APPLICATION_REVIEW_SETTINGS"
        );
        verify(settingsService).update(false);
    }
}
