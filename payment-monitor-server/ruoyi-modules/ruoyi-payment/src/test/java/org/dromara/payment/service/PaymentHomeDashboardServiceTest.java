package org.dromara.payment.service;

import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.domain.vo.PaymentHomeDashboardVo;
import org.dromara.payment.mapper.AmountSlotMapper;
import org.dromara.payment.mapper.MerchantApiAuditMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.PaymentDeviceMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.ReconciliationItemMapper;
import org.dromara.payment.mapper.SensitiveOperationLogMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PaymentHomeDashboardServiceTest {

    @Test
    void allMerchantHealthUsesBulkLoadedData() {
        MerchantMapper merchantMapper = mock(MerchantMapper.class);
        PaymentDeviceMapper deviceMapper = mock(PaymentDeviceMapper.class);
        PaymentEventMapper eventMapper = mock(PaymentEventMapper.class);
        PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
        WebhookOutboxMapper outboxMapper = mock(WebhookOutboxMapper.class);
        MerchantApiAuditMapper apiAuditMapper = mock(MerchantApiAuditMapper.class);
        SensitiveOperationLogMapper sensitiveMapper = mock(SensitiveOperationLogMapper.class);
        ReconciliationItemMapper reconciliationMapper = mock(ReconciliationItemMapper.class);
        AmountSlotMapper amountSlotMapper = mock(AmountSlotMapper.class);

        PmMerchant merchant = merchant(1L);
        when(merchantMapper.selectList(any())).thenReturn(List.of(merchant));
        when(eventMapper.selectList(any()))
            .thenReturn(List.of(), List.of(event(1L)));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(deviceMapper.selectList(any())).thenReturn(List.of(device(1L)));
        when(outboxMapper.selectList(any())).thenReturn(List.of(deadOutbox(1L)));
        when(apiAuditMapper.selectCount(any())).thenReturn(0L);
        when(sensitiveMapper.selectCount(any())).thenReturn(0L);
        when(reconciliationMapper.selectCount(any())).thenReturn(0L);
        when(amountSlotMapper.selectCount(any())).thenReturn(0L);

        PaymentHomeDashboardService service = new PaymentHomeDashboardService(
            merchantMapper,
            deviceMapper,
            eventMapper,
            orderMapper,
            outboxMapper,
            apiAuditMapper,
            sensitiveMapper,
            reconciliationMapper,
            amountSlotMapper,
            mock(PaymentReconciliationService.class),
            new PaymentProperties());

        MerchantContext.set(
            MerchantContext.PLATFORM_ACCOUNT,
            MerchantContext.ALL_SCOPE,
            null,
            true,
            MerchantContext.PLATFORM_TIMEZONE);
        try {
            PaymentHomeDashboardVo result = service.dashboard();
            PaymentHomeDashboardVo.MerchantHealthVo health =
                result.getMerchantHealth().getFirst();

            assertEquals(1, health.totalDevices());
            assertEquals(1, health.webhookDead());
            assertEquals(1, health.pendingReviewEvents());
            verify(deviceMapper, times(1)).selectList(any());
            verify(outboxMapper, times(1)).selectList(any());
            verify(eventMapper, times(2)).selectList(any());
            verify(outboxMapper, never()).selectCount(any());
            verify(eventMapper, never()).selectCount(any());
        } finally {
            MerchantContext.clear();
        }
    }

    private PmMerchant merchant(Long id) {
        PmMerchant merchant = new PmMerchant();
        merchant.setId(id);
        merchant.setMerchantCode("M" + id);
        merchant.setName("Merchant " + id);
        merchant.setStatus("0");
        return merchant;
    }

    private PmDevice device(Long merchantId) {
        PmDevice device = new PmDevice();
        device.setId(10L);
        device.setMerchantId(merchantId);
        device.setStatus(PaymentConstants.DEVICE_STATUS_ENABLED);
        device.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
        device.setMonitoringEnabled(true);
        device.setListenerConnected(true);
        device.setForegroundRunning(true);
        device.setNotificationAccessGranted(true);
        device.setBatteryOptimizationIgnored(true);
        return device;
    }

    private PmPaymentEvent event(Long merchantId) {
        PmPaymentEvent event = new PmPaymentEvent();
        event.setId(20L);
        event.setMerchantId(merchantId);
        event.setStatus(PaymentConstants.EVENT_STATUS_RECEIVED);
        return event;
    }

    private PmWebhookOutbox deadOutbox(Long merchantId) {
        PmWebhookOutbox outbox = new PmWebhookOutbox();
        outbox.setId(30L);
        outbox.setMerchantId(merchantId);
        outbox.setStatus("DEAD");
        return outbox;
    }
}
