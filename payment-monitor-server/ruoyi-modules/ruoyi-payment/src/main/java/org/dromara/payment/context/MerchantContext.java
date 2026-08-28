package org.dromara.payment.context;

import org.dromara.common.core.exception.ServiceException;

import java.util.Collection;
import java.util.Objects;

public final class MerchantContext {
    public static final String PLATFORM_ACCOUNT = "PLATFORM_ADMIN";
    public static final String MERCHANT_ACCOUNT = "MERCHANT_USER";
    public static final String ALL_SCOPE = "ALL";
    public static final String MERCHANT_SCOPE = "MERCHANT";
    public static final String PLATFORM_TIMEZONE = "Asia/Shanghai";

    private static final ThreadLocal<State> LOCAL = new ThreadLocal<>();

    private MerchantContext() {
    }

    public static void set(
        String accountType,
        String scopeMode,
        Long merchantId,
        boolean canAccessAllMerchants,
        String displayTimezone
    ) {
        LOCAL.set(new State(
            accountType,
            scopeMode,
            merchantId,
            canAccessAllMerchants,
            displayTimezone == null || displayTimezone.isBlank()
                ? PLATFORM_TIMEZONE
                : displayTimezone));
    }

    /**
     * 保留给内部任务和既有测试使用的兼容入口。管理端请求应由拦截器建立完整上下文。
     */
    public static void set(Long merchantId, boolean platformAccount) {
        set(
            platformAccount ? PLATFORM_ACCOUNT : MERCHANT_ACCOUNT,
            MERCHANT_SCOPE,
            merchantId,
            platformAccount,
            PLATFORM_TIMEZONE);
    }

    public static Long requireMerchantId() {
        State state = LOCAL.get();
        if (state == null || state.merchantId() == null) {
            throw new ServiceException("未建立支付商户上下文");
        }
        return state.merchantId();
    }

    public static Long merchantId() {
        State state = LOCAL.get();
        return state == null ? null : state.merchantId();
    }

    public static String accountType() {
        return requireState().accountType();
    }

    public static String scopeMode() {
        return requireState().scopeMode();
    }

    public static String displayTimezone() {
        return requireState().displayTimezone();
    }

    public static boolean isPlatformAccount() {
        State state = LOCAL.get();
        return state != null && PLATFORM_ACCOUNT.equals(state.accountType());
    }

    public static boolean canAccessAllMerchants() {
        State state = LOCAL.get();
        return state != null && state.canAccessAllMerchants();
    }

    public static boolean isSuperAdmin() {
        return canAccessAllMerchants();
    }

    public static Long resolveQueryMerchantId(Long requestedMerchantId) {
        State state = requireState();
        if (state.canAccessAllMerchants()) {
            if (state.merchantId() != null
                && requestedMerchantId != null
                && !state.merchantId().equals(requestedMerchantId)) {
                throw new ServiceException("查询商户与当前数据范围不一致");
            }
            return requestedMerchantId == null ? state.merchantId() : requestedMerchantId;
        }
        if (requestedMerchantId != null && !requestedMerchantId.equals(state.merchantId())) {
            throw new ServiceException("禁止访问其他商户数据");
        }
        return requireMerchantId();
    }

    public static Long requireTargetMerchantId(Long requestedMerchantId) {
        State state = requireState();
        if (state.canAccessAllMerchants()) {
            if (requestedMerchantId == null) {
                throw new ServiceException("平台管理员必须明确指定目标商户");
            }
            return requestedMerchantId;
        }
        if (requestedMerchantId != null && !requestedMerchantId.equals(state.merchantId())) {
            throw new ServiceException("禁止访问其他商户数据");
        }
        return requireMerchantId();
    }

    public static void requireAccessibleMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new ServiceException("商户 ID 不能为空");
        }
        State state = requireState();
        if (!state.canAccessAllMerchants() && !Objects.equals(state.merchantId(), merchantId)) {
            throw new ServiceException("禁止访问其他商户数据");
        }
    }

    public static Long requireSingleAccessibleMerchant(Collection<Long> merchantIds) {
        if (merchantIds == null || merchantIds.isEmpty()) {
            throw new ServiceException("未找到可操作的数据");
        }
        var distinct = merchantIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() != 1) {
            throw new ServiceException("同一次批量操作只能处理一个商户");
        }
        Long merchantId = distinct.getFirst();
        requireAccessibleMerchant(merchantId);
        return merchantId;
    }

    public static void clear() {
        LOCAL.remove();
    }

    private static State requireState() {
        State state = LOCAL.get();
        if (state == null) {
            throw new ServiceException("未建立支付访问上下文");
        }
        return state;
    }

    private record State(
        String accountType,
        String scopeMode,
        Long merchantId,
        boolean canAccessAllMerchants,
        String displayTimezone
    ) {
    }
}
