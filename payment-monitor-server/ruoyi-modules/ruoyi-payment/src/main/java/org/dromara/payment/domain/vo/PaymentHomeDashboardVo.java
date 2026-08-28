package org.dromara.payment.domain.vo;

import lombok.Builder;
import lombok.Data;
import org.dromara.payment.domain.PmReconciliationRun;

import java.util.List;

@Data
@Builder
public class PaymentHomeDashboardVo {
    private boolean superAdmin;
    private String scopeMode;
    private String displayTimezone;
    private Long merchantId;
    private String merchantName;
    private long merchantCount;
    private long enabledMerchantCount;
    private long todayEvents;
    private long todayPaidOrders;
    private long notificationConfirmedOrders;
    private long manuallyConfirmedOrders;
    private long reconciledOrders;
    private long sensitiveOperationsToday;
    private long openReconciliationDifferences;
    private long activeAmountSlots;
    private long coolingAmountSlots;
    private long todayIncomeAmountMinor;
    private long totalDevices;
    private long onlineDevices;
    private long unhealthyDevices;
    private long pendingReviewEvents;
    private long unmatchedIncomeEvents;
    private long conflictOrders;
    private long suspectedDuplicateEvents;
    private long webhookBacklog;
    private long webhookDead;
    private long merchantApiFailures24h;
    private long averageSyncLatencyMs;
    private long p95SyncLatencyMs;
    private PmReconciliationRun reconciliation;
    private List<PaymentTrendPointVo> trend;
    private List<MerchantHealthVo> merchantHealth;

    public record MerchantHealthVo(
        Long merchantId,
        String merchantCode,
        String merchantName,
        long totalDevices,
        long onlineDevices,
        long unhealthyDevices,
        long webhookDead,
        long pendingReviewEvents
    ) {
    }
}
