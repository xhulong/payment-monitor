package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.mapper.MerchantMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantLifecycleService {
    private final MerchantMapper merchantMapper;
    private final MerchantOnboardingService onboardingService;

    public PmMerchant requireActive(Long merchantId) {
        PmMerchant merchant = refreshMerchant(merchantId);
        if (merchant == null) {
            throw new ServiceException("商户不存在");
        }
        if (!PaymentConstants.MERCHANT_ACTIVE.equals(merchant.getLifecycleStatus())) {
            throw new ServiceException(
                "商户尚未完成开通，请前往“商户入驻”完成开通清单后再创建订单");
        }
        return merchant;
    }

    public boolean isActive(Long merchantId) {
        PmMerchant merchant = refreshMerchant(merchantId);
        return merchant != null
            && PaymentConstants.MERCHANT_ACTIVE.equals(merchant.getLifecycleStatus());
    }

    private PmMerchant refreshMerchant(Long merchantId) {
        PmMerchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<PmMerchant>()
            .eq(PmMerchant::getId, merchantId)
            .last("limit 1"));
        if (merchant != null
            && PaymentConstants.MERCHANT_ONBOARDING.equals(merchant.getLifecycleStatus())) {
            merchant = onboardingService.refreshActivation(merchantId);
        }
        return merchant;
    }
}
