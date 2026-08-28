<template>
  <div class="p-2 payment-dashboard">
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :lg="6">
        <el-card v-loading="loading" shadow="hover" class="metric-card">
          <div class="metric-card__label">{{ card.label }}</div>
          <div class="metric-card__value">{{ card.value }}</div>
          <div class="metric-card__hint">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt-1">
      <el-col :xs="24" :lg="16">
        <el-card v-loading="loading" shadow="hover">
          <template #header>
            <div class="section-heading">
              <div>
                <h3>今日每小时收款趋势</h3>
                <p>按 {{ dashboard.displayTimezone }} 展示事件数量和收款金额。</p>
              </div>
              <el-button type="primary" plain icon="Refresh" @click="loadDashboard">刷新</el-button>
            </div>
          </template>
          <div class="trend-chart">
            <div v-for="point in dashboard.trend" :key="point.bucket" class="trend-column">
              <div class="trend-column__tooltip">
                {{ point.bucket }} / {{ point.incomeCount }} 笔 / {{ formatAmount(point.incomeAmountMinor) }}
              </div>
              <div
                class="trend-column__bar"
                :style="{ height: `${Math.max(4, (point.incomeAmountMinor / maxTrendAmount) * 160)}px` }"
              ></div>
              <span>{{ point.bucket.slice(0, 2) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card v-loading="loading" shadow="hover">
          <template #header><h3 class="card-title">平台收入分布</h3></template>
          <el-progress
            type="dashboard"
            :percentage="wechatPercentage"
            :color="'#07c160'"
            :format="() => `微信 ${wechatPercentage}%`"
          />
          <el-descriptions :column="1" border class="mt-3">
            <el-descriptions-item label="微信收入">
              {{ formatAmount(dashboard.wechatIncomeAmountMinor) }}
            </el-descriptions-item>
            <el-descriptions-item label="支付宝收入">
              {{ formatAmount(dashboard.alipayIncomeAmountMinor) }}
            </el-descriptions-item>
            <el-descriptions-item label="解析失败率">
              {{ formatPercent(dashboard.parseFailureRate) }}
            </el-descriptions-item>
            <el-descriptions-item label="P95 同步延迟">
              {{ dashboard.p95SyncLatencyMs }} ms
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="PaymentDashboard" lang="ts">
import { getPaymentDashboard } from '@/api/payment';
import type { PaymentDashboardVO } from '@/api/payment/types';
import { usePaymentMerchantScope } from '@/hooks/payment/useMerchantScope';

const loading = ref(false);
const emptyDashboard = (): PaymentDashboardVO => ({
  displayTimezone: 'Asia/Shanghai',
  todayEvents: 0,
  wechatEvents: 0,
  alipayEvents: 0,
  incomeEvents: 0,
  expenseEvents: 0,
  onlineDevices: 0,
  todayIncomeAmountMinor: 0,
  wechatIncomeAmountMinor: 0,
  alipayIncomeAmountMinor: 0,
  pendingReviewEvents: 0,
  parseFailureRate: 0,
  averageSyncLatencyMs: 0,
  p95SyncLatencyMs: 0,
  trend: []
});
const dashboard = ref<PaymentDashboardVO>(emptyDashboard());
const {
  merchantStore,
  watchScope
} = usePaymentMerchantScope();

const formatAmount = (minor: number) => `¥${(minor / 100).toFixed(2)}`;
const formatPercent = (rate: number) => `${(rate * 100).toFixed(2)}%`;

const cards = computed(() => [
  {
    key: 'income',
    label: '今日收款金额',
    value: formatAmount(dashboard.value.todayIncomeAmountMinor),
    hint: `${dashboard.value.incomeEvents} 笔收入事件`
  },
  {
    key: 'events',
    label: '今日通知事件',
    value: dashboard.value.todayEvents,
    hint: `微信 ${dashboard.value.wechatEvents} / 支付宝 ${dashboard.value.alipayEvents}`
  },
  {
    key: 'online',
    label: '在线设备',
    value: dashboard.value.onlineDevices,
    hint: '最近 180 秒存在心跳'
  },
  {
    key: 'review',
    label: '待审核事件',
    value: dashboard.value.pendingReviewEvents,
    hint: '状态为 RECEIVED'
  },
  {
    key: 'latency',
    label: '平均同步延迟',
    value: `${dashboard.value.averageSyncLatencyMs} ms`,
    hint: `P95 ${dashboard.value.p95SyncLatencyMs} ms`
  },
  {
    key: 'parse',
    label: '解析失败率',
    value: formatPercent(dashboard.value.parseFailureRate),
    hint: '金额缺失与方向冲突'
  },
  {
    key: 'wechat',
    label: '微信收入',
    value: formatAmount(dashboard.value.wechatIncomeAmountMinor),
    hint: `${dashboard.value.wechatEvents} 条微信事件`
  },
  {
    key: 'alipay',
    label: '支付宝收入',
    value: formatAmount(dashboard.value.alipayIncomeAmountMinor),
    hint: `${dashboard.value.alipayEvents} 条支付宝事件`
  }
]);

const maxTrendAmount = computed(() =>
  Math.max(1, ...dashboard.value.trend.map(item => item.incomeAmountMinor))
);
const wechatPercentage = computed(() => {
  const total = dashboard.value.wechatIncomeAmountMinor + dashboard.value.alipayIncomeAmountMinor;
  return total === 0 ? 0 : Math.round((dashboard.value.wechatIncomeAmountMinor / total) * 100);
});

const loadDashboard = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await getPaymentDashboard();
    if (scopeVersion !== merchantStore.scopeVersion) return;
    dashboard.value = { ...emptyDashboard(), ...response.data, trend: response.data.trend || [] };
  } finally {
    loading.value = false;
  }
};

watchScope(loadDashboard);
onMounted(loadDashboard);
</script>

<style scoped lang="scss">
.payment-dashboard .el-col {
  margin-bottom: 16px;
}

.metric-card {
  height: 100%;
}

.metric-card__label,
.metric-card__hint,
.section-heading p {
  color: var(--el-text-color-secondary);
}

.metric-card__label {
  font-size: 14px;
}

.metric-card__value {
  margin: 10px 0 6px;
  font-size: 28px;
  font-weight: 700;
}

.metric-card__hint,
.section-heading p {
  margin: 0;
  font-size: 12px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-heading h3,
.card-title {
  margin: 0 0 6px;
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  height: 210px;
  gap: 5px;
  overflow-x: auto;
}

.trend-column {
  position: relative;
  display: flex;
  flex: 1 0 22px;
  min-width: 22px;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;

  span {
    margin-top: 6px;
    color: var(--el-text-color-secondary);
    font-size: 10px;
  }

  &:hover .trend-column__tooltip {
    display: block;
  }
}

.trend-column__bar {
  width: 70%;
  min-height: 4px;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, #20b486, #409eff);
}

.trend-column__tooltip {
  position: absolute;
  bottom: 185px;
  z-index: 2;
  display: none;
  width: max-content;
  padding: 6px 8px;
  border-radius: 6px;
  color: #fff;
  background: #303133;
  font-size: 12px;
}
</style>
