package org.dromara.payment.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.mapper.MerchantMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class MerchantLifecycleServiceTest {

    @Test
    void activatesMerchantWhenChecklistHasJustCompleted() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantOnboardingService onboardingService = mock(MerchantOnboardingService.class);
        PmMerchant onboarding = merchant(7L, PaymentConstants.MERCHANT_ONBOARDING);
        PmMerchant active = merchant(7L, PaymentConstants.MERCHANT_ACTIVE);
        when(merchantMapper.selectOne(any())).thenReturn(onboarding);
        when(onboardingService.refreshActivation(7L)).thenReturn(active);

        MerchantLifecycleService service =
            new MerchantLifecycleService(merchantMapper, onboardingService);

        assertEquals(PaymentConstants.MERCHANT_ACTIVE,
            service.requireActive(7L).getLifecycleStatus());
        verify(onboardingService).refreshActivation(7L);
    }

    @Test
    void explainsIncompleteOnboardingInsteadOfReturningRawErrorCode() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        MerchantOnboardingService onboardingService = mock(MerchantOnboardingService.class);
        PmMerchant onboarding = merchant(8L, PaymentConstants.MERCHANT_ONBOARDING);
        when(merchantMapper.selectOne(any())).thenReturn(onboarding);
        when(onboardingService.refreshActivation(8L)).thenReturn(onboarding);

        MerchantLifecycleService service =
            new MerchantLifecycleService(merchantMapper, onboardingService);

        ServiceException exception =
            assertThrows(ServiceException.class, () -> service.requireActive(8L));
        assertEquals(
            "商户尚未完成开通，请前往“商户入驻”完成开通清单后再创建订单",
            exception.getMessage()
        );
    }

    private PmMerchant merchant(Long id, String lifecycleStatus) {
        PmMerchant merchant = new PmMerchant();
        merchant.setId(id);
        merchant.setLifecycleStatus(lifecycleStatus);
        return merchant;
    }
}
