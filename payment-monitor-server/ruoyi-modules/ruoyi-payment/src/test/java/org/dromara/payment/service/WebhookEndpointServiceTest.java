package org.dromara.payment.service;

import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.mapper.WebhookEndpointMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.security.DeviceSecretCipher;
import org.dromara.payment.security.WebhookUrlValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class WebhookEndpointServiceTest {

    @Test
    void enabledEndpointsRespectEventAndPlatformSubscriptions() {
        WebhookEndpointMapper mapper = mock(WebhookEndpointMapper.class);
        PmWebhookEndpoint wechatPaid = endpoint(
            1L,
            "payment.order.paid,payment.order.expired",
            "WECHAT");
        PmWebhookEndpoint allCancelled = endpoint(
            2L,
            "payment.order.cancelled",
            "ALL");
        when(mapper.selectList(any())).thenReturn(List.of(wechatPaid, allCancelled));
        WebhookEndpointService service = new WebhookEndpointService(
            mapper,
            mock(WebhookOutboxMapper.class),
            mock(DeviceSecretCipher.class),
            mock(WebhookUrlValidator.class),
            mock(org.dromara.payment.context.MerchantAccessService.class),
            mock(MerchantDisplayService.class));

        List<PmWebhookEndpoint> paid = service.enabledEndpoints(
            10L,
            "payment.order.paid",
            "WECHAT");
        List<PmWebhookEndpoint> cancelled = service.enabledEndpoints(
            10L,
            "payment.order.cancelled",
            "ALIPAY");

        assertEquals(List.of(wechatPaid), paid);
        assertEquals(List.of(allCancelled), cancelled);
    }

    private PmWebhookEndpoint endpoint(Long id, String eventTypes, String platform) {
        PmWebhookEndpoint endpoint = new PmWebhookEndpoint();
        endpoint.setId(id);
        endpoint.setEventTypes(eventTypes);
        endpoint.setPlatformFilter(platform);
        endpoint.setStatus("0");
        return endpoint;
    }
}
