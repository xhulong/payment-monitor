package org.dromara.payment.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantUser;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.MerchantUserMapper;
import org.dromara.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantAccessService {
    public static final String ALL_MERCHANTS_PERMISSION = "payment:scope:all";
    public static final String PLATFORM_REVIEWER_ROLE = "payment_platform_reviewer";

    private final MerchantMapper merchantMapper;
    private final MerchantUserMapper merchantUserMapper;

    public Resolution resolve(String requestedMerchantId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("登录状态无效");
        }
        boolean canAccessAllMerchants = canCurrentAccountAccessAllMerchants();
        if (canAccessAllMerchants) {
            Long merchantId = parseRequested(requestedMerchantId);
            PmMerchant merchant = merchantId == null ? null : requireMerchant(merchantId, false);
            return new Resolution(
                merchant,
                MerchantContext.PLATFORM_ACCOUNT,
                merchant == null ? MerchantContext.ALL_SCOPE : MerchantContext.MERCHANT_SCOPE,
                true,
                MerchantContext.PLATFORM_TIMEZONE);
        }
        if (isCurrentAccountPlatformReviewer()) {
            if (parseRequested(requestedMerchantId) != null) {
                throw new ServiceException("平台审核员无权选择支付数据商户范围");
            }
            return new Resolution(
                null,
                MerchantContext.PLATFORM_ACCOUNT,
                MerchantContext.ALL_SCOPE,
                false,
                MerchantContext.PLATFORM_TIMEZONE);
        }

        PmMerchantUser binding = merchantUserMapper.selectOne(
            new LambdaQueryWrapper<PmMerchantUser>()
                .eq(PmMerchantUser::getUserId, userId)
                .last("limit 1"));
        if (binding == null) {
            throw new ServiceException("当前用户未绑定支付商户");
        }
        Long requested = parseRequested(requestedMerchantId);
        if (requested != null && !requested.equals(binding.getMerchantId())) {
            throw new ServiceException("禁止访问其他商户数据");
        }
        PmMerchant merchant = requireMerchant(binding.getMerchantId(), true);
        return new Resolution(
            merchant,
            MerchantContext.MERCHANT_ACCOUNT,
            MerchantContext.MERCHANT_SCOPE,
            false,
            merchant.getTimezone());
    }

    public void requireAccessible(Long merchantId) {
        if (merchantId == null) {
            throw new ServiceException("商户 ID 不能为空");
        }
        MerchantContext.requireAccessibleMerchant(merchantId);
        requireMerchant(merchantId, false);
    }

    public Long requireTargetMerchant(Long requestedMerchantId, boolean requireEnabled) {
        Long merchantId = MerchantContext.requireTargetMerchantId(requestedMerchantId);
        requireMerchant(merchantId, requireEnabled);
        return merchantId;
    }

    public PmMerchant requireMerchant(Long merchantId, boolean requireEnabled) {
        PmMerchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ServiceException("商户不存在");
        }
        if (requireEnabled && !PaymentConstants.DEVICE_STATUS_ENABLED.equals(merchant.getStatus())) {
            throw new ServiceException("商户已停用");
        }
        return merchant;
    }

    private Long parseRequested(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new ServiceException("X-Merchant-Id 格式无效");
        }
    }

    public boolean canCurrentAccountAccessAllMerchants() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return false;
        }
        if (LoginHelper.isSuperAdmin(userId)) {
            return true;
        }
        LoginUser loginUser = LoginHelper.getLoginUser();
        Set<String> permissions = loginUser == null ? null : loginUser.getMenuPermission();
        return permissions != null
            && (permissions.contains("*:*:*") || permissions.contains(ALL_MERCHANTS_PERMISSION));
    }

    public boolean isCurrentAccountPlatformAccount() {
        return canCurrentAccountAccessAllMerchants() || isCurrentAccountPlatformReviewer();
    }

    private boolean isCurrentAccountPlatformReviewer() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        Set<String> roles = loginUser == null ? null : loginUser.getRolePermission();
        return roles != null && roles.contains(PLATFORM_REVIEWER_ROLE);
    }

    public record Resolution(
        PmMerchant merchant,
        String accountType,
        String scopeMode,
        boolean canAccessAllMerchants,
        String displayTimezone
    ) {
    }
}
