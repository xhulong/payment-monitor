<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <div class="toolbar">
          <div>
            <h3>内部对账中心</h3>
            <p>每次运行保留独立版本；“内部已对账”仅表示订单、通知和回调一致。</p>
          </div>
          <div>
            <payment-merchant-target-select
              v-if="showMerchantColumn"
              v-model="runMerchantId"
              style="width: 240px"
            />
            <el-date-picker v-model="businessDate" type="date" value-format="YYYY-MM-DD" placeholder="业务日期" />
            <el-button v-hasPermi="['payment:reconciliation:run']" type="primary" :loading="running" @click="createRun">
              执行对账
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="runs" border>
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="运行号" prop="runNo" min-width="230" />
        <el-table-column label="业务日期" prop="businessDate" width="120" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'BALANCED' ? 'success' : 'warning'">
              {{ row.status === 'BALANCED' ? '账目平衡' : '需要核对' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已支付订单" prop="paidOrderCount" width="110" align="right" />
        <el-table-column label="匹配收入" prop="matchedIncomeCount" width="100" align="right" />
        <el-table-column label="未匹配收入" prop="unmatchedIncomeCount" width="110" align="right" />
        <el-table-column label="金额差异" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amountDifferenceMinor) }}</template>
        </el-table-column>
        <el-table-column label="待处理差异" prop="openDifferenceCount" width="110" align="right" />
        <el-table-column label="已处理" prop="resolvedDifferenceCount" width="90" align="right" />
        <el-table-column label="完成时间" width="190">
          <template #default="{ row }">{{ formatApiTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="showRun(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="load"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="对账运行与差异" size="92%">
      <template v-if="detail">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="运行号" :span="2">{{ detail.run.runNo }}</el-descriptions-item>
          <el-descriptions-item label="业务日期">{{ detail.run.businessDate }}</el-descriptions-item>
          <el-descriptions-item label="时区">{{ detail.run.timezone }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ formatAmount(detail.run.paidOrderAmountMinor) }}</el-descriptions-item>
          <el-descriptions-item label="匹配收入">{{ formatAmount(detail.run.matchedIncomeAmountMinor) }}</el-descriptions-item>
          <el-descriptions-item label="未匹配收入">{{ formatAmount(detail.run.unmatchedIncomeAmountMinor) }}</el-descriptions-item>
          <el-descriptions-item label="差异">{{ formatAmount(detail.run.amountDifferenceMinor) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.items" border class="mt-4">
          <el-table-column label="差异类型" width="190">
            <template #default="{ row }">{{ differenceLabel(row.differenceType) }}</template>
          </el-table-column>
          <el-table-column label="说明" prop="description" min-width="260" />
          <el-table-column label="订单 / 事件 / 交易" min-width="300">
            <template #default="{ row }">
              {{ row.orderId || '-' }} / {{ row.eventId || '-' }} / {{ row.transactionId || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">{{ formatAmount(row.amountMinor) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }"><el-tag>{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="处理结果" prop="resolutionNote" min-width="180" />
          <el-table-column label="操作" fixed="right" width="220">
            <template #default="{ row }">
              <template v-if="row.status === 'OPEN'">
                <el-button v-hasPermi="['payment:reconciliation:resolve']" link type="primary" @click="resolve(row as ReconciliationItemVO, 'RESOLVE')">已处理</el-button>
                <el-button v-hasPermi="['payment:reconciliation:resolve']" link type="warning" @click="resolve(row as ReconciliationItemVO, 'IGNORE')">忽略</el-button>
                <el-button
                  v-if="row.orderId && row.transactionId"
                  v-hasPermi="['payment:reconciliation:resolve']"
                  link
                  type="success"
                  @click="resolve(row as ReconciliationItemVO, 'RECONCILE')"
                >
                  标记已对账
                </el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="PaymentReconciliation" lang="ts">
import {
  createReconciliationRun,
  getReconciliationRun,
  listReconciliationRuns,
  resolveReconciliationItem
} from '@/api/payment';
import { formatApiTime } from '@/api/payment/time';
import type {
  ReconciliationItemVO,
  ReconciliationRunDetailVO,
  ReconciliationVO
} from '@/api/payment/types';
import PaymentMerchantTargetSelect from '@/components/PaymentMerchantTargetSelect/index.vue';
import { usePaymentMerchantScope } from '@/hooks/payment/useMerchantScope';

const loading = ref(false);
const running = ref(false);
const total = ref(0);
const runs = ref<ReconciliationVO[]>([]);
const query = ref<PageQuery>({ pageNum: 1, pageSize: 10 });
const businessDate = ref<string>();
const runMerchantId = ref<string>();
const detail = ref<ReconciliationRunDetailVO>();
const detailVisible = ref(false);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();

const load = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listReconciliationRuns(query.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    runs.value = response.data?.rows || [];
    total.value = response.data?.total || 0;
  } finally {
    loading.value = false;
  }
};
const createRun = async () => {
  if (!runMerchantId.value) {
    ElMessage.warning('请选择目标商户');
    return;
  }
  running.value = true;
  try {
    const response = await createReconciliationRun(
      businessDate.value,
      runMerchantId.value
    );
    ElMessage.success(`对账完成：${response.data?.runNo || ''}`);
    await load();
  } finally {
    running.value = false;
  }
};
const showRun = async (id: string | number) => {
  detail.value = (await getReconciliationRun(id)).data;
  detailVisible.value = true;
};
const resolve = async (
  row: ReconciliationItemVO,
  action: 'RESOLVE' | 'IGNORE' | 'RECONCILE'
) => {
  const result = await ElMessageBox.prompt(
    `商户“${row.merchantName || row.merchantCode || row.merchantId}”的对账差异 ${row.id}：填写处理说明`,
    '处理对账差异',
    {
      inputType: 'textarea',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    }
  );
  await resolveReconciliationItem(row.id, action, result.value);
  if (detail.value) await showRun(detail.value.run.id!);
  await load();
  ElMessage.success('差异状态已更新');
};
const formatAmount = (minor?: number) => minor == null ? '-' : `${minor > 0 ? '+' : ''}¥${(minor / 100).toFixed(2)}`;
const differenceLabel = (value: string) => ({
  UNMATCHED_INCOME: '未匹配收入',
  NOTIFICATION_UNCONFIRMED: '通知确认未人工确认',
  AMOUNT_MISMATCH: '金额不一致',
  CONFLICT_ORDER: '冲突订单',
  SUSPECTED_DUPLICATE: '疑似重复',
  DEAD_WEBHOOK: 'DEAD Webhook',
  LATE_PAYMENT: '迟到付款'
})[value] || value;
watchScope(async () => {
  query.value.pageNum = 1;
  runMerchantId.value = defaultTargetMerchantId();
  detailVisible.value = false;
  await load();
});
onBeforeMount(async () => {
  if (!merchantStore.context) await merchantStore.load();
  runMerchantId.value = defaultTargetMerchantId();
});
onMounted(load);
</script>

<style scoped lang="scss">
.toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  h3 { margin: 0 0 6px; }
  p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
  > div:last-child { display: flex; gap: 10px; }
}
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
