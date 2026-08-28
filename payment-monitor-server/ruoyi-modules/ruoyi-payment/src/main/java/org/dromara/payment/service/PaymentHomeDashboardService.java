package org.dromara.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.payment.config.PaymentProperties;
import org.dromara.payment.constant.PaymentConstants;
import org.dromara.payment.context.MerchantContext;
import org.dromara.payment.domain.PmDevice;
import org.dromara.payment.domain.PmMerchant;
import org.dromara.payment.domain.PmMerchantApiAudit;
import org.dromara.payment.domain.PmPaymentEvent;
import org.dromara.payment.domain.PmPaymentOrder;
import org.dromara.payment.domain.PmWebhookOutbox;
import org.dromara.payment.domain.PmReconciliationItem;
import org.dromara.payment.domain.PmAmountSlotReservation;
import org.dromara.payment.domain.PmSensitiveOperationLog;
import org.dromara.payment.domain.vo.PaymentHomeDashboardVo;
import org.dromara.payment.domain.vo.PaymentTrendPointVo;
import org.dromara.payment.mapper.MerchantApiAuditMapper;
import org.dromara.payment.mapper.MerchantMapper;
import org.dromara.payment.mapper.PaymentDeviceMapper;
import org.dromara.payment.mapper.PaymentEventMapper;
import org.dromara.payment.mapper.PaymentOrderMapper;
import org.dromara.payment.mapper.WebhookOutboxMapper;
import org.dromara.payment.mapper.ReconciliationItemMapper;
import org.dromara.payment.mapper.AmountSlotMapper;
import org.dromara.payment.mapper.SensitiveOperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentHomeDashboardService {
    private final MerchantMapper merchantMapper;
    private final PaymentDeviceMapper deviceMapper;
    private final PaymentEventMapper eventMapper;
    private final PaymentOrderMapper orderMapper;
    private final WebhookOutboxMapper outboxMapper;
    private final MerchantApiAuditMapper apiAuditMapper;
    private final SensitiveOperationLogMapper sensitiveOperationLogMapper;
    private final ReconciliationItemMapper reconciliationItemMapper;
    private final AmountSlotMapper amountSlotMapper;
    private final PaymentReconciliationService reconciliationService;
    private final PaymentProperties properties;

    public PaymentHomeDashboardVo dashboard() {
        boolean platformAccount = MerchantContext.canAccessAllMerchants();
        Long merchantId = MerchantContext.resolveQueryMerchantId(null);
        PmMerchant currentMerchant = merchantId == null ? null : merchantMapper.selectById(merchantId);
        ZoneId zone = ZoneId.of(MerchantContext.displayTimezone());
        OffsetDateTime begin = LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
        LambdaQueryWrapper<PmPaymentEvent> eventQuery = new LambdaQueryWrapper<PmPaymentEvent>()
            .ge(PmPaymentEvent::getReceivedAt, begin)
            .orderByAsc(PmPaymentEvent::getReceivedAt);
        LambdaQueryWrapper<PmPaymentOrder> orderQuery = new LambdaQueryWrapper<PmPaymentOrder>()
            .ge(PmPaymentOrder::getCreatedAt, begin);
        LambdaQueryWrapper<PmDevice> deviceQuery = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<PmWebhookOutbox> outboxQuery = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            eventQuery.eq(PmPaymentEvent::getMerchantId, merchantId);
            orderQuery.eq(PmPaymentOrder::getMerchantId, merchantId);
            deviceQuery.eq(PmDevice::getMerchantId, merchantId);
            outboxQuery.eq(PmWebhookOutbox::getMerchantId, merchantId);
        }
        List<PmPaymentEvent> todayEvents = eventMapper.selectList(eventQuery);
        List<PmPaymentOrder> todayOrders = orderMapper.selectList(orderQuery);
        List<PmDevice> devices = deviceMapper.selectList(deviceQuery);
        List<PmWebhookOutbox> outboxes = outboxMapper.selectList(outboxQuery);
        LambdaQueryWrapper<PmPaymentEvent> pendingReviewQuery =
            new LambdaQueryWrapper<PmPaymentEvent>()
                .eq(PmPaymentEvent::getStatus, PaymentConstants.EVENT_STATUS_RECEIVED);
        if (merchantId != null) {
            pendingReviewQuery.eq(PmPaymentEvent::getMerchantId, merchantId);
        }
        List<PmPaymentEvent> pendingReviewEvents = eventMapper.selectList(pendingReviewQuery);
        OffsetDateTime onlineThreshold = OffsetDateTime.now()
            .minusSeconds(properties.getHeartbeat().getOnlineThresholdSeconds());
        long onlineDevices = devices.stream()
            .filter(item -> isOnline(item, onlineThreshold))
            .count();
        long unhealthyDevices = devices.stream()
            .filter(item -> isUnhealthy(item, onlineThreshold))
            .count();
        List<Long> latencies = todayEvents.stream()
            .map(this::syncLatency)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        List<PmMerchant> merchants = platformAccount && merchantId == null
            ? merchantMapper.selectList(new LambdaQueryWrapper<PmMerchant>()
                .orderByAsc(PmMerchant::getName))
            : currentMerchant == null ? List.of() : List.of(currentMerchant);
        Map<Long, List<PmDevice>> devicesByMerchant = devices.stream()
            .collect(Collectors.groupingBy(PmDevice::getMerchantId));
        Map<Long, Long> deadOutboxByMerchant = outboxes.stream()
            .filter(item -> "DEAD".equals(item.getStatus()))
            .collect(Collectors.groupingBy(
                PmWebhookOutbox::getMerchantId,
                Collectors.counting()));
        Map<Long, Long> pendingReviewByMerchant = pendingReviewEvents.stream()
            .collect(Collectors.groupingBy(
                PmPaymentEvent::getMerchantId,
                Collectors.counting()));
        long apiFailures = apiAuditMapper.selectCount(
            scopedApiAudit(merchantId)
                .eq(PmMerchantApiAudit::getSuccess, false)
                .ge(PmMerchantApiAudit::getCreatedAt, OffsetDateTime.now().minusHours(24)));
        LambdaQueryWrapper<PmSensitiveOperationLog> sensitiveOperationQuery =
            new LambdaQueryWrapper<PmSensitiveOperationLog>()
                .ge(PmSensitiveOperationLog::getOperatedAt, begin);
        LambdaQueryWrapper<PmReconciliationItem> differenceQuery =
            new LambdaQueryWrapper<PmReconciliationItem>()
                .eq(PmReconciliationItem::getStatus, "OPEN");
        LambdaQueryWrapper<PmAmountSlotReservation> slotQuery =
            new LambdaQueryWrapper<PmAmountSlotReservation>();
        if (merchantId != null) {
            sensitiveOperationQuery.eq(PmSensitiveOperationLog::getMerchantId, merchantId);
            differenceQuery.eq(PmReconciliationItem::getMerchantId, merchantId);
            slotQuery.eq(PmAmountSlotReservation::getMerchantId, merchantId);
        }
        return PaymentHomeDashboardVo.builder()
            .superAdmin(platformAccount)
            .scopeMode(MerchantContext.scopeMode())
            .displayTimezone(MerchantContext.displayTimezone())
            .merchantId(merchantId)
            .merchantName(currentMerchant == null ? null : currentMerchant.getName())
            .merchantCount(merchants.size())
            .enabledMerchantCount(merchants.stream()
                .filter(item -> PaymentConstants.DEVICE_STATUS_ENABLED.equals(item.getStatus()))
                .count())
            .todayEvents(todayEvents.size())
            .todayPaidOrders(todayOrders.stream()
                .filter(item -> PaymentConstants.ORDER_STATUS_PAID.equals(item.getStatus()))
                .count())
            .notificationConfirmedOrders(todayOrders.stream()
                .filter(item -> PaymentConstants.CONFIRMATION_NOTIFICATION.equals(item.getConfirmationStatus()))
                .count())
            .manuallyConfirmedOrders(todayOrders.stream()
                .filter(item -> PaymentConstants.CONFIRMATION_MANUAL.equals(item.getConfirmationStatus()))
                .count())
            .reconciledOrders(todayOrders.stream()
                .filter(item -> PaymentConstants.CONFIRMATION_RECONCILED.equals(item.getConfirmationStatus()))
                .count())
            .sensitiveOperationsToday(
                sensitiveOperationLogMapper.selectCount(sensitiveOperationQuery))
            .openReconciliationDifferences(reconciliationItemMapper.selectCount(differenceQuery))
            .activeAmountSlots(amountSlotMapper.selectCount(slotQuery.clone()
                .eq(PmAmountSlotReservation::getStatus, PaymentConstants.SLOT_ACTIVE)))
            .coolingAmountSlots(amountSlotMapper.selectCount(slotQuery.clone()
                .eq(PmAmountSlotReservation::getStatus, PaymentConstants.SLOT_COOLING)))
            .todayIncomeAmountMinor(todayEvents.stream()
                .filter(item -> "INCOME".equals(item.getDirection()))
                .map(PmPaymentEvent::getAmountMinor)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum())
            .totalDevices(devices.size())
            .onlineDevices(onlineDevices)
            .unhealthyDevices(unhealthyDevices)
            .pendingReviewEvents(todayEvents.stream()
                .filter(item -> PaymentConstants.EVENT_STATUS_RECEIVED.equals(item.getStatus()))
                .count())
            .unmatchedIncomeEvents(todayEvents.stream()
                .filter(item -> "INCOME".equals(item.getDirection()))
                .filter(item -> !PaymentConstants.EVENT_STATUS_MATCHED.equals(item.getStatus()))
                .count())
            .conflictOrders(todayOrders.stream()
                .filter(item -> PaymentConstants.ORDER_STATUS_CONFLICT.equals(item.getStatus()))
                .count())
            .suspectedDuplicateEvents(todayEvents.stream()
                .filter(item -> PaymentConstants.DUPLICATE_STATUS_SUSPECTED.equals(item.getDuplicateStatus()))
                .count())
            .webhookBacklog(outboxes.stream()
                .filter(item -> List.of("PENDING", "RETRYING", "DELIVERING").contains(item.getStatus()))
                .count())
            .webhookDead(outboxes.stream().filter(item -> "DEAD".equals(item.getStatus())).count())
            .merchantApiFailures24h(apiFailures)
            .averageSyncLatencyMs(latencies.isEmpty() ? 0
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0)))
            .p95SyncLatencyMs(percentile95(latencies))
            .reconciliation(merchantId == null ? null : reconciliationService.previewToday(merchantId))
            .trend(buildTrend(todayEvents, zone))
            .merchantHealth(merchants.stream()
                .map(item -> merchantHealth(
                    item,
                    devicesByMerchant.getOrDefault(item.getId(), List.of()),
                    deadOutboxByMerchant.getOrDefault(item.getId(), 0L),
                    pendingReviewByMerchant.getOrDefault(item.getId(), 0L),
                    onlineThreshold))
                .toList())
            .build();
    }

    private PaymentHomeDashboardVo.MerchantHealthVo merchantHealth(
        PmMerchant merchant,
        List<PmDevice> devices,
        long dead,
        long pendingReview,
        OffsetDateTime onlineThreshold
    ) {
        return new PaymentHomeDashboardVo.MerchantHealthVo(
            merchant.getId(),
            merchant.getMerchantCode(),
            merchant.getName(),
            devices.size(),
            devices.stream().filter(item -> isOnline(item, onlineThreshold)).count(),
            devices.stream().filter(item -> isUnhealthy(item, onlineThreshold)).count(),
            dead,
            pendingReview);
    }

    private boolean isOnline(PmDevice device, OffsetDateTime threshold) {
        return PaymentConstants.DEVICE_STATUS_ENABLED.equals(device.getStatus())
            && device.getLastSeenAt() != null
            && !device.getLastSeenAt().isBefore(threshold);
    }

    private boolean isUnhealthy(PmDevice device, OffsetDateTime threshold) {
        if (!PaymentConstants.DEVICE_STATUS_ENABLED.equals(device.getStatus())) {
            return false;
        }
        return !isOnline(device, threshold)
            || Boolean.FALSE.equals(device.getMonitoringEnabled())
            || Boolean.FALSE.equals(device.getListenerConnected())
            || Boolean.FALSE.equals(device.getForegroundRunning())
            || Boolean.FALSE.equals(device.getNotificationAccessGranted())
            || Boolean.FALSE.equals(device.getBatteryOptimizationIgnored())
            || StringUtils.isNotBlank(device.getLastHealthIssue());
    }

    private Long syncLatency(PmPaymentEvent event) {
        if (event.getClientReceivedAtMs() == null || event.getReceivedAt() == null) {
            return null;
        }
        return Math.max(
            0,
            event.getReceivedAt().toInstant().toEpochMilli() - event.getClientReceivedAtMs());
    }

    private long percentile95(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(sortedValues.size() * 0.95) - 1);
        return sortedValues.get(index);
    }

    private List<PaymentTrendPointVo> buildTrend(
        List<PmPaymentEvent> events,
        ZoneId zone
    ) {
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            buckets.put(String.format("%02d:00", hour), new long[3]);
        }
        for (PmPaymentEvent event : events) {
            String bucket = String.format(
                "%02d:00",
                event.getReceivedAt().atZoneSameInstant(zone).getHour());
            long[] values = buckets.get(bucket);
            values[0]++;
            if ("INCOME".equals(event.getDirection())) {
                values[1]++;
                values[2] += event.getAmountMinor() == null ? 0 : event.getAmountMinor();
            }
        }
        List<PaymentTrendPointVo> result = new ArrayList<>();
        buckets.forEach((bucket, values) ->
            result.add(new PaymentTrendPointVo(bucket, values[0], values[1], values[2])));
        return result;
    }

    private LambdaQueryWrapper<PmMerchantApiAudit> scopedApiAudit(Long merchantId) {
        LambdaQueryWrapper<PmMerchantApiAudit> query = new LambdaQueryWrapper<>();
        if (merchantId != null) {
            query.eq(PmMerchantApiAudit::getMerchantId, merchantId);
        }
        return query;
    }
}
