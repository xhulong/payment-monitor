package org.dromara.payment.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class MerchantContextInterceptor implements HandlerInterceptor {
    private final MerchantAccessService accessService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MerchantContext.clear();
        MerchantAccessService.Resolution resolution =
            accessService.resolve(request.getHeader("X-Merchant-Id"));
        Long merchantId = resolution.merchant() == null ? null : resolution.merchant().getId();
        MerchantContext.set(
            resolution.accountType(),
            resolution.scopeMode(),
            merchantId,
            resolution.canAccessAllMerchants(),
            resolution.displayTimezone());
        if (resolution.merchant() != null) {
            request.setAttribute("paymentMerchantCode", resolution.merchant().getMerchantCode());
            request.setAttribute("paymentMerchantName", resolution.merchant().getName());
        }
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {
        MerchantContext.clear();
    }
}
