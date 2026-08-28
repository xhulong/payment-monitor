package org.dromara.payment.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 支付监控概览。
 */
@Data
@Builder
public class PaymentDashboardVo {
    private String displayTimezone;
    private long todayEvents;
    private long wechatEvents;
    private long alipayEvents;
    private long incomeEvents;
    private long expenseEvents;
    private long onlineDevices;
    private long todayIncomeAmountMinor;
    private long wechatIncomeAmountMinor;
    private long alipayIncomeAmountMinor;
    private long pendingReviewEvents;
    private double parseFailureRate;
    private long averageSyncLatencyMs;
    private long p95SyncLatencyMs;
    private List<PaymentTrendPointVo> trend;
}
