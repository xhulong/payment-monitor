package org.dromara.payment.service;

import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmWebhookEndpoint;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.mapper.WebhookDeliveryLogMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.context.MerchantContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WebhookOutboxServiceTest {

    @Test
    void enqueueAssignsPrimaryKeyAndPersistsStablePayload() throws Exception {
        WebhookOutboxMapper outboxMapper = mock(WebhookOutboxMapper.class);
        WebhookEndpointService endpointService = mock(WebhookEndpointService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PmWebhookEndpoint endpoint = new PmWebhookEndpoint();
        endpoint.setId(8L);
        when(endpointService.enabledEndpoints(
            1L,
            "payment.order.paid",
            "WECHAT"
        )).thenReturn(List.of(endpoint));

        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(10L);
        order.setMerchantId(1L);
        order.setMerchantOrderNo("ORDER-10");
        order.setPlatform("WECHAT");
        order.setRequestedAmountMinor(100L);
        order.setPayableAmountMinor(123L);
        order.setCurrency("CNY");
        order.setStatus("PAID");
        order.setPaidAt(OffsetDateTime.now(ZoneOffset.UTC));
        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(20L);
        event.setClientEventId("event-20");
        event.setEventTime(order.getPaidAt());

        WebhookOutboxService service = new WebhookOutboxService(
            outboxMapper,
            mock(WebhookDeliveryLogMapper.class),
            endpointService,
            objectMapper,
            mock(MerchantDisplayService.class)
        );
        service.enqueueOrderPaid(order, event);

        ArgumentCaptor<PmWebhookOutbox> captor = ArgumentCaptor.forClass(PmWebhookOutbox.class);
        verify(outboxMapper).insertOnConflict(captor.capture());
        PmWebhookOutbox outbox = captor.getValue();
        assertNotNull(outbox.getId());
        assertNotNull(outbox.getDeliveryId());
        assertNotNull(outbox.getEventId());
        assertEquals(2, outbox.getSchemaVersion());
        assertEquals(8L, outbox.getEndpointId());
        assertEquals(10L, outbox.getAggregateId());
        assertEquals("PENDING", outbox.getStatus());
        Map<?, ?> payload = objectMapper.readValue(outbox.getPayload(), Map.class);
        assertEquals(2, ((Number) payload.get("schemaVersion")).intValue());
        assertEquals(outbox.getDeliveryId(), payload.get("deliveryId"));
        assertEquals(outbox.getEventId(), payload.get("eventId"));
        assertEquals("payment.order.paid", payload.get("type"));
    }

    @Test
    void replayCreatesNewDeliveryAndLinksOriginal() throws Exception {
        WebhookOutboxMapper outboxMapper = mock(WebhookOutboxMapper.class);
        WebhookEndpointService endpointService = mock(WebhookEndpointService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PmWebhookOutbox original = new PmWebhookOutbox();
        original.setId(31L);
        original.setDeliveryId("delivery-original");
        original.setEventId("event-stable");
        original.setSchemaVersion(2);
        original.setMerchantId(1L);
        original.setEndpointId(8L);
        original.setAggregateType("PAYMENT_ORDER");
        original.setAggregateId(10L);
        original.setEventType("payment.order.paid");
        original.setPayload("""
            {
              "schemaVersion": 1,
              "deliveryId": "delivery-original",
              "type": "payment.order.paid",
              "data": {"orderId": 10}
            }
            """);
        PmWebhookEndpoint endpoint = new PmWebhookEndpoint();
        endpoint.setId(8L);
        endpoint.setMerchantId(1L);
        when(outboxMapper.selectById(31L)).thenReturn(original);
        when(endpointService.requireForMerchant(8L, 1L)).thenReturn(endpoint);
        WebhookOutboxService service = new WebhookOutboxService(
            outboxMapper,
            mock(WebhookDeliveryLogMapper.class),
            endpointService,
            objectMapper,
            mock(MerchantDisplayService.class));
        MerchantContext.set(1L, false);
        try {
            service.replay(31L, "人工补发");
        } finally {
            MerchantContext.clear();
        }

        ArgumentCaptor<PmWebhookOutbox> captor =
            ArgumentCaptor.forClass(PmWebhookOutbox.class);
        verify(outboxMapper).insertOnConflict(captor.capture());
        PmWebhookOutbox replay = captor.getValue();
        assertEquals("delivery-original", replay.getReplayOfDeliveryId());
        assertEquals("event-stable", replay.getEventId());
        assertEquals("人工补发", replay.getReplayReason());
        assertEquals("PENDING", replay.getStatus());
        assertNotNull(replay.getDeliveryId());
        Map<?, ?> payload = objectMapper.readValue(replay.getPayload(), Map.class);
        assertEquals(2, ((Number) payload.get("schemaVersion")).intValue());
        assertEquals(replay.getDeliveryId(), payload.get("deliveryId"));
        assertEquals("event-stable", payload.get("eventId"));
        assertTrue(payload.containsKey("replayedAt"));
    }
}
