package org.dromara.payment.integration.epay.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegration;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegrationRoute;
import org.dromara.payment.integration.epay.mapper.PaymentIntegrationRouteMapper;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

@Component
@RequiredArgsConstructor
public class EpayPayTypeResolver {
    private final PaymentIntegrationRouteMapper routeMapper;

    public String resolve(PmPaymentIntegration integration, String requestedType) {
        if (requestedType != null && !requestedType.isBlank()) {
            return normalize(requestedType);
        }
        var routes = routeMapper.selectList(
            new LambdaQueryWrapper<PmPaymentIntegrationRoute>()
                .eq(PmPaymentIntegrationRoute::getIntegrationId, integration.getId())
                .eq(PmPaymentIntegrationRoute::getMerchantId, integration.getMerchantId())
                .eq(PmPaymentIntegrationRoute::getStatus, "0")
                .orderByAsc(PmPaymentIntegrationRoute::getPriority)
                .orderByAsc(PmPaymentIntegrationRoute::getId)
        );
        var payTypes = new LinkedHashSet<String>();
        for (PmPaymentIntegrationRoute route : routes) {
            payTypes.add(normalize(route.getPayType()));
        }
        if (payTypes.isEmpty()) {
            throw new EpayException("缺少参数 type，且当前接入应用未配置可用支付路由");
        }
        if (payTypes.size() > 1) {
            throw new EpayException("缺少参数 type，当前接入应用配置了多个支付方式");
        }
        return payTypes.getFirst();
    }

    private String normalize(String value) {
        String type = value == null ? "" : value.trim().toLowerCase();
        if (!"alipay".equals(type) && !"wxpay".equals(type)) {
            throw new EpayException("不支持的支付类型");
        }
        return type;
    }
}
