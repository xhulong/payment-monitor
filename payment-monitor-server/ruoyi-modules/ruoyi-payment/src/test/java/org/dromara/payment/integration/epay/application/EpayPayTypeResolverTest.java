package org.dromara.payment.integration.epay.application;

import org.dromara.payment.integration.epay.domain.PmPaymentIntegration;
import org.dromara.payment.integration.epay.domain.PmPaymentIntegrationRoute;
import org.dromara.payment.integration.epay.mapper.PaymentIntegrationRouteMapper;
import org.dromara.payment.integration.epay.protocol.EpayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class EpayPayTypeResolverTest {

    @Test
    void keepsExplicitClassicPayTypeWithoutLoadingRoutes() {
        PaymentIntegrationRouteMapper mapper = mock(PaymentIntegrationRouteMapper.class);
        EpayPayTypeResolver resolver = new EpayPayTypeResolver(mapper);

        assertEquals("wxpay", resolver.resolve(integration(), " WXPAY "));
        verify(mapper, never()).selectList(any());
    }

    @Test
    void infersMissingTypeWhenApplicationHasOnlyOneEnabledPayType() {
        PaymentIntegrationRouteMapper mapper = mock(PaymentIntegrationRouteMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
            route(1L, "wxpay"),
            route(2L, "wxpay")
        ));
        EpayPayTypeResolver resolver = new EpayPayTypeResolver(mapper);

        assertEquals("wxpay", resolver.resolve(integration(), null));
    }

    @Test
    void rejectsMissingTypeWhenApplicationHasMultiplePayTypes() {
        PaymentIntegrationRouteMapper mapper = mock(PaymentIntegrationRouteMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
            route(1L, "wxpay"),
            route(2L, "alipay")
        ));
        EpayPayTypeResolver resolver = new EpayPayTypeResolver(mapper);

        EpayException exception = assertThrows(
            EpayException.class,
            () -> resolver.resolve(integration(), "")
        );
        assertEquals("缺少参数 type，当前接入应用配置了多个支付方式", exception.getMessage());
    }

    @Test
    void rejectsMissingTypeWhenApplicationHasNoEnabledRoute() {
        PaymentIntegrationRouteMapper mapper = mock(PaymentIntegrationRouteMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        EpayPayTypeResolver resolver = new EpayPayTypeResolver(mapper);

        EpayException exception = assertThrows(
            EpayException.class,
            () -> resolver.resolve(integration(), null)
        );
        assertEquals("缺少参数 type，且当前接入应用未配置可用支付路由", exception.getMessage());
    }

    private PmPaymentIntegration integration() {
        PmPaymentIntegration integration = new PmPaymentIntegration();
        integration.setId(9001L);
        integration.setMerchantId(19001L);
        return integration;
    }

    private PmPaymentIntegrationRoute route(Long id, String payType) {
        PmPaymentIntegrationRoute route = new PmPaymentIntegrationRoute();
        route.setId(id);
        route.setPayType(payType);
        return route;
    }
}
