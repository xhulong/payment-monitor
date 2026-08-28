package org.dromara.payment.service;

import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmQrAsset;
import org.dromara.payment.domain.dto.PaymentOrderCreateRequest;
import org.dromara.payment.domain.vo.OrderMatchCandidateVo;
import org.dromara.payment.domain.vo.PaymentOrderVo;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.mapper.OrderMatchAuditMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.QrAssetMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentOrderServiceTest {

    @Test
    void retriesAnotherCentOffsetWhenAnActiveAmountIsOccupied() {
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        QrAssetService qrAssetService = mock(QrAssetService.class);
        PmQrAsset asset = new PmQrAsset();
        asset.setId(10L);
        asset.setPlatform("WECHAT");
        when(qrAssetService.requireEnabled(
            PaymentConstants.DEFAULT_MERCHANT_ID, 10L, "WECHAT")).thenReturn(asset);
        when(orderMapper.insertOnConflict(any(PmPaymentOrder.class))).thenReturn(0, 1);
        PaymentOrderVo stored = new PaymentOrderVo();
        stored.setId(99L);
        stored.setQrAssetId(10L);
        stored.setPublicToken("x".repeat(43));
        when(orderMapper.selectVoById(any())).thenReturn(stored);

        PaymentOrderService service = service(orderMapper, qrAssetService);
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        request.setPlatform("WECHAT");
        request.setQrAssetId(10L);
        request.setAmountMinor(100L);
        request.setExpiresSeconds(300);
        MerchantContext.set(PaymentConstants.DEFAULT_MERCHANT_ID, true);
        try {
            service.create(request);
        } finally {
            MerchantContext.clear();
        }

        ArgumentCaptor<PmPaymentOrder> captor = ArgumentCaptor.forClass(PmPaymentOrder.class);
        verify(orderMapper, org.mockito.Mockito.times(2)).insertOnConflict(captor.capture());
        assertEquals(100L, captor.getAllValues().get(0).getPayableAmountMinor());
        assertEquals(0, captor.getAllValues().get(0).getAmountOffsetMinor());
        assertEquals(101L, captor.getAllValues().get(1).getPayableAmountMinor());
        assertEquals(1, captor.getAllValues().get(1).getAmountOffsetMinor());
    }

    @Test
    void skipsReservedSlotAndUsesNextSmallestOffset() {
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        QrAssetService qrAssetService = mock(QrAssetService.class);
        AmountSlotService amountSlotService = mock(AmountSlotService.class);
        PmQrAsset asset = new PmQrAsset();
        asset.setId(10L);
        asset.setPlatform("WECHAT");
        when(qrAssetService.requireEnabled(
            PaymentConstants.DEFAULT_MERCHANT_ID, 10L, "WECHAT")).thenReturn(asset);
        when(orderMapper.insertOnConflict(any(PmPaymentOrder.class))).thenReturn(1);
        when(amountSlotService.reserve(any(PmPaymentOrder.class))).thenReturn(false, true);

        PaymentOrderService service = service(
            orderMapper,
            qrAssetService,
            mock(PaymentEventMapper.class),
            mock(WebhookOutboxService.class),
            amountSlotService
        );
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest();
        request.setPlatform("WECHAT");
        request.setQrAssetId(10L);
        request.setAmountMinor(100L);
        request.setExpiresSeconds(300);
        MerchantContext.set(PaymentConstants.DEFAULT_MERCHANT_ID, true);
        try {
            service.create(request);
        } finally {
            MerchantContext.clear();
        }

        ArgumentCaptor<PmPaymentOrder> captor = ArgumentCaptor.forClass(PmPaymentOrder.class);
        verify(orderMapper, org.mockito.Mockito.times(2)).insertOnConflict(captor.capture());
        assertEquals(100L, captor.getAllValues().get(0).getPayableAmountMinor());
        assertEquals(101L, captor.getAllValues().get(1).getPayableAmountMinor());
        verify(orderMapper).deleteById(captor.getAllValues().get(0).getId());
    }

    @Test
    void matchCandidatesPutExactEventsFirstAndFilterInvalidStatuses() {
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(20L);
        order.setMerchantId(1L);
        order.setPlatform("WECHAT");
        order.setPayableAmountMinor(100L);
        when(orderMapper.selectOne(any())).thenReturn(order);

        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        PmPaymentEvent mismatch = event(
            31L,
            "ALIPAY",
            99L,
            PaymentConstants.EVENT_STATUS_RECEIVED,
            PaymentConstants.DUPLICATE_STATUS_NONE,
            timestamp
        );
        PmPaymentEvent exact = event(
            32L,
            "WECHAT",
            100L,
            PaymentConstants.EVENT_STATUS_REVIEWED,
            PaymentConstants.DUPLICATE_STATUS_SUSPECTED,
            timestamp.minusMinutes(1)
        );
        PmPaymentEvent confirmedDuplicate = event(
            33L,
            "WECHAT",
            100L,
            PaymentConstants.EVENT_STATUS_RECEIVED,
            PaymentConstants.DUPLICATE_STATUS_CONFIRMED,
            timestamp.plusMinutes(1)
        );
        when(eventMapper.selectManualMatchCandidates(
            1L,
            20L,
            "WECHAT",
            100L,
            200
        )).thenReturn(List.of(mismatch, exact, confirmedDuplicate));

        PaymentOrderService service = service(
            orderMapper,
            mock(QrAssetService.class),
            eventMapper
        );
        MerchantContext.set(1L, false);
        List<OrderMatchCandidateVo> candidates;
        try {
            candidates = service.matchCandidates(20L);
        } finally {
            MerchantContext.clear();
        }

        assertEquals(List.of(32L, 31L),
            candidates.stream().map(OrderMatchCandidateVo::getId).toList());
        assertTrue(candidates.getFirst().isExactMatch());
        assertFalse(candidates.getLast().isExactMatch());
    }

    @Test
    void autoMatchesOneIncomeEventAndMarksBothSidesPaid() {
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
        PmPaymentOrder order = new PmPaymentOrder();
        order.setId(20L);
        order.setMerchantId(1L);
        order.setStatus(PaymentConstants.ORDER_STATUS_PENDING);
        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(30L);
        event.setMerchantId(1L);
        event.setPlatform("ALIPAY");
        event.setDirection("INCOME");
        event.setAmountMinor(123L);
        event.setStatus(PaymentConstants.EVENT_STATUS_RECEIVED);
        event.setEventTime(OffsetDateTime.now(ZoneOffset.UTC));
        event.setReceivedAt(event.getEventTime());
        when(orderMapper.selectMatchCandidatesForUpdate(any(), any(), any(), any()))
            .thenReturn(List.of(order));
        when(eventMapper.selectByIdForUpdate(30L, 1L)).thenReturn(event);

        WebhookOutboxService webhookOutboxService = mock(WebhookOutboxService.class);
        PaymentOrderService service = service(
            orderMapper,
            mock(QrAssetService.class),
            eventMapper,
            webhookOutboxService
        );
        service.autoMatch(event);

        assertEquals(PaymentConstants.ORDER_STATUS_PAID, order.getStatus());
        assertEquals(30L, order.getMatchedEventId());
        assertEquals(PaymentConstants.EVENT_STATUS_MATCHED, event.getStatus());
        verify(orderMapper).updateById(order);
        verify(eventMapper).updateById(event);
        verify(webhookOutboxService).enqueueOrderPaid(order, event);
    }

    @Test
    void staleExpirationScanCannotOverwritePaidOrder() {
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        PmPaymentOrder candidate = new PmPaymentOrder();
        candidate.setId(77L);
        candidate.setMerchantId(1L);
        candidate.setStatus(PaymentConstants.ORDER_STATUS_PENDING);
        candidate.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        when(orderMapper.selectList(any())).thenReturn(List.of(candidate));
        PmPaymentOrder locked = new PmPaymentOrder();
        locked.setId(77L);
        locked.setMerchantId(1L);
        locked.setStatus(PaymentConstants.ORDER_STATUS_PAID);
        locked.setExpiresAt(candidate.getExpiresAt());
        when(orderMapper.selectByIdForUpdate(77L, 1L)).thenReturn(locked);
        WebhookOutboxService webhook = mock(WebhookOutboxService.class);
        PaymentOrderService service = service(
            orderMapper,
            mock(QrAssetService.class),
            mock(PaymentEventMapper.class),
            webhook
        );

        service.expirePendingOrders();

        verify(orderMapper, never()).updateById(locked);
        verify(webhook, never()).enqueueOrderEvent(any(), any(), any());
    }

    private PaymentOrderService service(
        PaymentOrderMapper orderMapper,
        QrAssetService qrAssetService
    ) {
        return service(orderMapper, qrAssetService, mock(PaymentEventMapper.class));
    }

    private PaymentOrderService service(
        PaymentOrderMapper orderMapper,
        QrAssetService qrAssetService,
        PaymentEventMapper eventMapper
    ) {
        return service(orderMapper, qrAssetService, eventMapper, mock(WebhookOutboxService.class));
    }

    private PaymentOrderService service(
        PaymentOrderMapper orderMapper,
        QrAssetService qrAssetService,
        PaymentEventMapper eventMapper,
        WebhookOutboxService webhookOutboxService
    ) {
        AmountSlotService amountSlotService = mock(AmountSlotService.class);
        when(amountSlotService.reserve(any(PmPaymentOrder.class))).thenReturn(true);
        return service(
            orderMapper,
            qrAssetService,
            eventMapper,
            webhookOutboxService,
            amountSlotService
        );
    }

    private PaymentOrderService service(
        PaymentOrderMapper orderMapper,
        QrAssetService qrAssetService,
        PaymentEventMapper eventMapper,
        WebhookOutboxService webhookOutboxService,
        AmountSlotService amountSlotService
    ) {
        PaymentProperties properties = new PaymentProperties();
        properties.setPublicBaseUrl("http://127.0.0.1:8080");
        org.dromara.payment.context.MerchantAccessService merchantAccessService =
            mock(org.dromara.payment.context.MerchantAccessService.class);
        when(merchantAccessService.requireTargetMerchant(null, true))
            .thenReturn(PaymentConstants.DEFAULT_MERCHANT_ID);
        return new PaymentOrderService(
            orderMapper,
            mock(QrAssetMapper.class),
            mock(OrderMatchAuditMapper.class),
            eventMapper,
            qrAssetService,
            properties,
            webhookOutboxService,
            mock(PaymentTransactionService.class),
            amountSlotService,
            mock(SensitiveOperationLogService.class),
            mock(MerchantLifecycleService.class),
            merchantAccessService,
            mock(MerchantDisplayService.class)
        );
    }

    private PmPaymentEvent event(
        Long id,
        String platform,
        Long amountMinor,
        String status,
        String duplicateStatus,
        OffsetDateTime eventTime
    ) {
        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(id);
        event.setMerchantId(1L);
        event.setClientEventId("CLIENT-" + id);
        event.setPlatform(platform);
        event.setDirection("INCOME");
        event.setAmountMinor(amountMinor);
        event.setCurrency("CNY");
        event.setStatus(status);
        event.setDuplicateStatus(duplicateStatus);
        event.setEventTime(eventTime);
        event.setReceivedAt(eventTime);
        return event;
    }
}
