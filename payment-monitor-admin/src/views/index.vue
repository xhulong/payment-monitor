<template>
  <div class="home-page">
    <section class="welcome-card">
      <div>
        <div class="welcome-kicker">{{ greeting }}</div>
        <h1>{{ dashboardTitle }}</h1>
        <p>{{ dashboardSubtitle }}</p>
      </div>
      <div class="welcome-actions">
        <el-button
          v-if="canViewPayment"
          type="primary"
          icon="Refresh"
          :loading="loading"
          @click="loadDashboard"
        >
          刷新数据
        </el-button>
        <el-button
          v-if="canViewPayment && !dashboard.superAdmin"
          icon="DataAnalysis"
          :loading="reconciling"
          @click="runReconciliation"
        >
          立即对账
        </el-button>
      </div>
    </section>

    <template v-if="canViewPayment">
      <el-row :gutter="16">
        <el-col v-for="card in metricCards" :key="card.key" :xs="24" :sm="12" :lg="6">
          <article
            v-loading="loading"
            class="metric-card"
            :class="[
              `metric-card--${card.tone}`,
              { 'is-clickable': canOpenPaymentRoute(card.permission) }
            ]"
            @click="openRoute(card.route, card.permission)"
          >
            <div class="metric-card__label">{{ card.label }}</div>
            <strong>{{ card.value }}</strong>
            <span>{{ card.hint }}</span>
          </article>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :xl="16">
          <section class="panel-card">
            <div class="panel-heading">
              <div>
                <span>实时经营数据</span>
                <h2>今日收款趋势</h2>
              </div>
              <div class="latency-badge">
                P95 同步 {{ dashboard.p95SyncLatencyMs }} ms
              </div>
            </div>
            <div v-loading="loading" class="trend-chart">
              <div v-for="point in dashboard.trend" :key="point.bucket" class="trend-column">
                <el-tooltip
                  :content="`${point.bucket} · ${point.incomeCount} 笔 · ${formatAmount(point.incomeAmountMinor)}`"
                  placement="top"
                >
                  <div
                    class="trend-column__bar"
                    :style="{ height: `${Math.max(4, point.incomeAmountMinor / maxTrendAmount * 170)}px` }"
                  />
                </el-tooltip>
                <span>{{ point.bucket.slice(0, 2) }}</span>
              </div>
            </div>
          </section>
        </el-col>

        <el-col :xs="24" :xl="8">
          <section class="panel-card">
            <div class="panel-heading">
              <div>
                <span>需要关注</span>
                <h2>异常待办</h2>
              </div>
            </div>
            <div class="attention-list">
              <button
                v-for="item in attentionItems"
                :key="item.key"
                type="button"
                class="attention-item"
                @click="openRoute(item.route, item.permission)"
              >
                <span class="attention-item__dot" :class="`is-${item.tone}`" />
                <span class="attention-item__body">
                  <strong>{{ item.label }}</strong>
                  <small>{{ item.hint }}</small>
                </span>
                <b>{{ item.value }}</b>
              </button>
            </div>
          </section>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :xl="dashboard.superAdmin ? 24 : 12">
          <section v-if="dashboard.superAdmin" class="panel-card">
            <div class="panel-heading">
              <div>
                <span>平台管理员视图</span>
                <h2>商户运行状态</h2>
              </div>
              <el-tag type="info">{{ dashboard.enabledMerchantCount }}/{{ dashboard.merchantCount }} 个商户启用</el-tag>
            </div>
            <el-table :data="dashboard.merchantHealth" border stripe>
              <el-table-column label="商户" min-width="190">
                <template #default="{ row }">
                  <strong>{{ row.merchantName }}</strong>
                  <div class="table-subtitle">{{ row.merchantCode }}</div>
                </template>
              </el-table-column>
              <el-table-column label="设备" width="130" align="center">
                <template #default="{ row }">{{ row.onlineDevices }}/{{ row.totalDevices }} 在线</template>
              </el-table-column>
              <el-table-column label="异常设备" prop="unhealthyDevices" width="110" align="center" />
              <el-table-column label="待审核" prop="pendingReviewEvents" width="100" align="center" />
              <el-table-column label="回调终止" prop="webhookDead" width="100" align="center" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="merchantHealthy(row as MerchantHealthVO) ? 'success' : 'danger'">
                    {{ merchantHealthy(row as MerchantHealthVO) ? '正常' : '需处理' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </section>

          <section v-else class="panel-card">
            <div class="panel-heading">
              <div>
                <span>经营核对</span>
                <h2>今日对账</h2>
              </div>
              <el-tag :type="reconciliationBalanced ? 'success' : 'warning'">
                {{ reconciliationBalanced ? '账目平衡' : '需要核对' }}
              </el-tag>
            </div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="已支付订单">
                {{ dashboard.reconciliation?.paidOrderCount || 0 }} 笔
              </el-descriptions-item>
              <el-descriptions-item label="订单金额">
                {{ formatAmount(dashboard.reconciliation?.paidOrderAmountMinor || 0) }}
              </el-descriptions-item>
              <el-descriptions-item label="已匹配收款">
                {{ dashboard.reconciliation?.matchedIncomeCount || 0 }} 笔
              </el-descriptions-item>
              <el-descriptions-item label="未匹配收款">
                {{ dashboard.reconciliation?.unmatchedIncomeCount || 0 }} 笔
              </el-descriptions-item>
              <el-descriptions-item label="金额差异">
                {{ formatSignedAmount(dashboard.reconciliation?.amountDifferenceMinor || 0) }}
              </el-descriptions-item>
              <el-descriptions-item label="Webhook 终止">
                {{ dashboard.reconciliation?.webhookDeadCount || 0 }}
              </el-descriptions-item>
            </el-descriptions>
          </section>
        </el-col>

        <el-col v-if="!dashboard.superAdmin" :xs="24" :xl="12">
          <section class="panel-card quick-panel">
            <div class="panel-heading">
              <div>
                <span>常用操作</span>
                <h2>支付工作台</h2>
              </div>
            </div>
            <div class="quick-grid">
              <button
                v-for="item in quickActions"
                :key="item.route"
                type="button"
                @click="openRoute(item.route, item.permission)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <strong>{{ item.label }}</strong>
                <span>{{ item.hint }}</span>
              </button>
            </div>
          </section>
        </el-col>
      </el-row>
    </template>

    <section v-else class="panel-card empty-home">
      <el-empty description="当前角色未开通支付监控权限">
        <template #description>
          <p>你好，{{ userStore.nickname || `用户 ${userStore.userId}` }}。当前首页只展示已授权的业务内容。</p>
        </template>
      </el-empty>
    </section>
  </div>
</template>

<script setup name="Index" lang="ts">
import { getPaymentHomeDashboard, runPaymentReconciliation } from '@/api/payment';
import type { MerchantHealthVO, PaymentHomeDashboardVO } from '@/api/payment/types';
import { useMerchantStore } from '@/store/modules/merchant';
import { useUserStore } from '@/store/modules/user';
import {
  filterAuthorizedPaymentItems,
  hasPaymentPermission,
  isNavigableRouteMatch,
  PAYMENT_ROUTE_PERMISSIONS,
  PAYMENT_ROUTES
} from '@/utils/payment-routes';

const router = useRouter();
const userStore = useUserStore();
const merchantStore = useMerchantStore();
const loading = ref(false);
const reconciling = ref(false);
const canViewPayment = computed(() =>
  userStore.permissions.includes('*:*:*') ||
  userStore.permissions.includes('payment:dashboard:view')
);

const emptyDashboard = (): PaymentHomeDashboardVO => ({
  superAdmin: userStore.roles.includes('admin'),
  scopeMode: 'ALL',
  displayTimezone: 'Asia/Shanghai',
  merchantId: undefined,
  merchantCount: 0,
  enabledMerchantCount: 0,
  todayEvents: 0,
  todayPaidOrders: 0,
  notificationConfirmedOrders: 0,
  manuallyConfirmedOrders: 0,
  reconciledOrders: 0,
  sensitiveOperationsToday: 0,
  openReconciliationDifferences: 0,
  activeAmountSlots: 0,
  coolingAmountSlots: 0,
  todayIncomeAmountMinor: 0,
  totalDevices: 0,
  onlineDevices: 0,
  unhealthyDevices: 0,
  pendingReviewEvents: 0,
  unmatchedIncomeEvents: 0,
  conflictOrders: 0,
  suspectedDuplicateEvents: 0,
  webhookBacklog: 0,
  webhookDead: 0,
  merchantApiFailures24h: 0,
  averageSyncLatencyMs: 0,
  p95SyncLatencyMs: 0,
  trend: [],
  merchantHealth: []
});
const dashboard = ref<PaymentHomeDashboardVO>(emptyDashboard());

const greeting = computed(() => {
  const hour = new Date().getHours();
  return hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好';
});
const dashboardTitle = computed(() =>
  !canViewPayment.value
    ? `欢迎回来，${userStore.nickname || `用户 ${userStore.userId}`}`
    : dashboard.value.superAdmin
      ? '支付平台运行总览'
      : `${dashboard.value.merchantName || merchantStore.context?.merchantName || '商户'}经营首页`
);
const dashboardSubtitle = computed(() =>
  !canViewPayment.value
    ? `当前角色：${userStore.roles.join('、') || '普通用户'}`
    : dashboard.value.superAdmin
      ? `汇总各商户的设备、收款、订单、Webhook 和异常状态；“今日”按 ${dashboard.value.displayTimezone} 统计。`
      : '集中查看今日收款、设备健康、回调状态和对账结果。'
);

const formatAmount = (minor: number) => `¥${(minor / 100).toFixed(2)}`;
const formatSignedAmount = (minor: number) =>
  `${minor > 0 ? '+' : ''}${formatAmount(minor)}`;
const metricCards = computed(() => dashboard.value.superAdmin
  ? [
      { key: 'merchant', label: '启用商户', value: dashboard.value.enabledMerchantCount, hint: `共 ${dashboard.value.merchantCount} 个商户`, tone: 'blue', route: PAYMENT_ROUTES.merchant, permission: PAYMENT_ROUTE_PERMISSIONS.merchant },
      { key: 'income', label: '今日收款', value: formatAmount(dashboard.value.todayIncomeAmountMinor), hint: `${dashboard.value.todayPaidOrders} 个订单已支付`, tone: 'green', route: PAYMENT_ROUTES.order, permission: PAYMENT_ROUTE_PERMISSIONS.order },
      { key: 'device', label: '在线设备', value: `${dashboard.value.onlineDevices}/${dashboard.value.totalDevices}`, hint: `${dashboard.value.unhealthyDevices} 台需要处理`, tone: dashboard.value.unhealthyDevices ? 'orange' : 'blue', route: PAYMENT_ROUTES.device, permission: PAYMENT_ROUTE_PERMISSIONS.device },
      { key: 'sensitive-operation', label: '敏感操作', value: dashboard.value.sensitiveOperationsToday, hint: '今日敏感确认执行', tone: 'blue', route: PAYMENT_ROUTES.sensitiveOperation, permission: PAYMENT_ROUTE_PERMISSIONS.sensitiveOperation }
    ]
  : [
      { key: 'notification', label: '通知确认', value: dashboard.value.notificationConfirmedOrders, hint: '已支付但仍需人工确认', tone: dashboard.value.notificationConfirmedOrders ? 'orange' : 'blue', route: PAYMENT_ROUTES.transaction, permission: PAYMENT_ROUTE_PERMISSIONS.transaction },
      { key: 'manual', label: '人工已确认', value: dashboard.value.manuallyConfirmedOrders, hint: `${dashboard.value.reconciledOrders} 笔内部已对账`, tone: 'green', route: PAYMENT_ROUTES.transaction, permission: PAYMENT_ROUTE_PERMISSIONS.transaction },
      { key: 'sensitive-operation', label: '敏感操作', value: dashboard.value.sensitiveOperationsToday, hint: '强制补单或撤销确认', tone: 'blue', route: PAYMENT_ROUTES.sensitiveOperation, permission: PAYMENT_ROUTE_PERMISSIONS.sensitiveOperation },
      { key: 'difference', label: '对账差异', value: dashboard.value.openReconciliationDifferences, hint: `${dashboard.value.coolingAmountSlots} 个金额槽位冷却中`, tone: dashboard.value.openReconciliationDifferences ? 'orange' : 'green', route: PAYMENT_ROUTES.reconciliation, permission: PAYMENT_ROUTE_PERMISSIONS.reconciliation }
    ]);

const allAttentionItems = computed(() => [
  { key: 'device', label: '设备健康异常', hint: '离线、监听断开或权限失效', value: dashboard.value.unhealthyDevices, tone: 'danger', route: PAYMENT_ROUTES.device, permission: PAYMENT_ROUTE_PERMISSIONS.device },
  { key: 'unmatched', label: '未匹配收款', hint: '已收到收入通知但未支付订单', value: dashboard.value.unmatchedIncomeEvents, tone: 'warning', route: PAYMENT_ROUTES.event, permission: PAYMENT_ROUTE_PERMISSIONS.event },
  { key: 'duplicate', label: '疑似重复事件', hint: '等待人工确认或排除', value: dashboard.value.suspectedDuplicateEvents, tone: 'warning', route: PAYMENT_ROUTES.event, permission: PAYMENT_ROUTE_PERMISSIONS.event },
  { key: 'difference', label: '未解决对账差异', hint: '内部订单、通知和回调不一致', value: dashboard.value.openReconciliationDifferences, tone: 'warning', route: PAYMENT_ROUTES.reconciliation, permission: PAYMENT_ROUTE_PERMISSIONS.reconciliation },
  { key: 'webhook', label: 'Webhook 终止', hint: '超过重试次数仍未送达', value: dashboard.value.webhookDead, tone: 'danger', route: PAYMENT_ROUTES.webhook, permission: PAYMENT_ROUTE_PERMISSIONS.webhook },
  { key: 'api', label: 'API 鉴权失败', hint: '最近 24 小时商户 API 失败', value: dashboard.value.merchantApiFailures24h, tone: 'info', route: PAYMENT_ROUTES.merchant, permission: PAYMENT_ROUTE_PERMISSIONS.merchant }
]);
const attentionItems = computed(() =>
  filterAuthorizedPaymentItems(allAttentionItems.value, userStore.permissions)
);
const totalAttention = computed(() =>
  allAttentionItems.value.reduce((total, item) => total + item.value, 0)
);
const maxTrendAmount = computed(() =>
  Math.max(1, ...dashboard.value.trend.map(item => item.incomeAmountMinor))
);
const reconciliationBalanced = computed(() =>
  dashboard.value.reconciliation?.status === 'BALANCED'
);
const merchantHealthy = (row: MerchantHealthVO) =>
  row.unhealthyDevices === 0 && row.webhookDead === 0;

const allQuickActions = [
  { label: '创建订单', hint: '生成动态金额支付订单', icon: 'ShoppingCart', route: PAYMENT_ROUTES.order, permission: PAYMENT_ROUTE_PERMISSIONS.order },
  { label: '支付交易', hint: '确认通知级支付并查看交易状态', icon: 'List', route: PAYMENT_ROUTES.transaction, permission: PAYMENT_ROUTE_PERMISSIONS.transaction },
  { label: '敏感操作记录', hint: '查看 MFA 或会话确认的高风险操作', icon: 'Lock', route: PAYMENT_ROUTES.sensitiveOperation, permission: PAYMENT_ROUTE_PERMISSIONS.sensitiveOperation },
  { label: '对账中心', hint: '运行版本化内部对账', icon: 'DataAnalysis', route: PAYMENT_ROUTES.reconciliation, permission: PAYMENT_ROUTE_PERMISSIONS.reconciliation },
  { label: '设备状态', hint: '检查手机监听与同步健康', icon: 'Cellphone', route: PAYMENT_ROUTES.device, permission: PAYMENT_ROUTE_PERMISSIONS.device },
  { label: '支付事件', hint: '处理未匹配和疑似重复', icon: 'Tickets', route: PAYMENT_ROUTES.event, permission: PAYMENT_ROUTE_PERMISSIONS.event },
  { label: 'Webhook', hint: '测试、重放和查看投递', icon: 'Connection', route: PAYMENT_ROUTES.webhook, permission: PAYMENT_ROUTE_PERMISSIONS.webhook }
];
const quickActions = computed(() =>
  filterAuthorizedPaymentItems(allQuickActions, userStore.permissions)
);

const loadDashboard = async () => {
  if (!canViewPayment.value) return;
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    if (!merchantStore.context) await merchantStore.load();
    const response = await getPaymentHomeDashboard();
    if (scopeVersion !== merchantStore.scopeVersion) return;
    dashboard.value = {
      ...emptyDashboard(),
      ...response.data,
      trend: response.data?.trend || [],
      merchantHealth: response.data?.merchantHealth || []
    };
  } finally {
    loading.value = false;
  }
};
const runReconciliation = async () => {
  reconciling.value = true;
  try {
    await runPaymentReconciliation();
    ElMessage.success('今日对账已完成');
    await loadDashboard();
  } finally {
    reconciling.value = false;
  }
};
const canOpenPaymentRoute = (permission: string) =>
  hasPaymentPermission(userStore.permissions, permission);
const openRoute = async (route: string, permission: string) => {
  if (!canOpenPaymentRoute(permission)) return;
  const resolved = router.resolve(route);
  if (!isNavigableRouteMatch(resolved.matched.map(item => item.path))) {
    ElMessage.warning('该功能入口暂时不可用，请从左侧菜单进入');
    return;
  }
  try {
    await router.push(resolved.fullPath);
  } catch {
    ElMessage.warning('页面打开失败，请稍后重试');
  }
};
watch(
  () => merchantStore.scopeVersion,
  () => {
    void loadDashboard();
  }
);
onMounted(loadDashboard);
</script>

<style scoped lang="scss">
.home-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-card,
.panel-card,
.metric-card {
  border: 1px solid var(--app-surface-border);
  background: var(--app-surface-bg);
  box-shadow: var(--app-shadow-sm);
}

.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 28px;
  border-radius: 24px;
  background:
    radial-gradient(circle at 8% 0, rgba(53, 109, 255, 0.18), transparent 32%),
    var(--app-surface-bg);

  h1 {
    margin: 4px 0 8px;
    color: var(--app-text-title);
    font-size: clamp(26px, 4vw, 38px);
    letter-spacing: -0.04em;
  }

  p {
    margin: 0;
    color: var(--app-text-muted);
  }
}

.welcome-kicker {
  color: var(--app-accent-strong);
  font-size: 13px;
  font-weight: 700;
}

.welcome-actions {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
}

.metric-card {
  min-height: 126px;
  margin-bottom: 16px;
  padding: 20px;
  border-radius: 20px;
  transition: transform 0.2s ease;

  &.is-clickable {
    cursor: pointer;
  }

  &.is-clickable:hover {
    transform: translateY(-2px);
  }

  strong {
    display: block;
    margin: 10px 0 6px;
    color: var(--app-text-title);
    font-size: 28px;
  }

  span,
  .metric-card__label {
    color: var(--app-text-muted);
    font-size: 13px;
  }
}

.metric-card--green { border-top: 3px solid #18a66a; }
.metric-card--blue { border-top: 3px solid #356dff; }
.metric-card--orange { border-top: 3px solid #e6a23c; }
.metric-card--red { border-top: 3px solid #f56c6c; }

.panel-card {
  height: calc(100% - 16px);
  margin-bottom: 16px;
  padding: 22px;
  border-radius: 22px;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;

  span {
    color: var(--app-text-muted);
    font-size: 12px;
  }

  h2 {
    margin: 3px 0 0;
    color: var(--app-text-title);
    font-size: 21px;
  }
}

.latency-badge {
  padding: 8px 12px;
  border-radius: 999px;
  color: #356dff;
  background: rgba(53, 109, 255, 0.1);
  font-size: 12px;
  font-weight: 700;
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  min-height: 210px;
  gap: 6px;
  overflow-x: auto;
}

.trend-column {
  display: flex;
  flex: 1 0 24px;
  min-width: 24px;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;

  span {
    margin-top: 7px;
    color: var(--app-text-muted);
    font-size: 10px;
  }
}

.trend-column__bar {
  width: 70%;
  min-height: 4px;
  border-radius: 6px 6px 2px 2px;
  background: linear-gradient(180deg, #20b486, #356dff);
}

.attention-list {
  display: grid;
  gap: 10px;
}

.attention-item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--app-surface-border);
  border-radius: 14px;
  color: inherit;
  background: var(--app-elevated-soft-bg);
  text-align: left;
  cursor: pointer;

  b {
    color: var(--app-text-title);
    font-size: 20px;
  }
}

.attention-item__dot {
  width: 9px;
  height: 9px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #909399;
}
.attention-item__dot.is-danger { background: #f56c6c; }
.attention-item__dot.is-warning { background: #e6a23c; }
.attention-item__dot.is-info { background: #409eff; }

.attention-item__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;

  strong { color: var(--app-text-title); }
  small { color: var(--app-text-muted); }
}

.table-subtitle {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  button {
    display: flex;
    min-height: 112px;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 7px;
    padding: 16px;
    border: 1px solid var(--app-surface-border);
    border-radius: 16px;
    color: inherit;
    background: var(--app-elevated-soft-bg);
    cursor: pointer;
    text-align: left;
  }

  .el-icon {
    color: #356dff;
    font-size: 22px;
  }

  strong { color: var(--app-text-title); }
  span { color: var(--app-text-muted); font-size: 12px; }
}

.empty-home {
  min-height: 380px;
}

@media (max-width: 720px) {
  .welcome-card {
    align-items: flex-start;
    flex-direction: column;
    padding: 20px;
  }

  .welcome-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
