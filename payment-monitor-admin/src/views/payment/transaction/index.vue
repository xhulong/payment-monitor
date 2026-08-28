<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <div class="toolbar">
          <div>
            <h3>支付交易与金额槽位</h3>
            <p>区分通知观察、订单匹配、人工确认和内部已对账，不将通知确认误认为最终资金确认。</p>
          </div>
          <el-button icon="Refresh" @click="refresh">刷新</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="支付交易" name="transactions">
          <el-form :model="transactionQuery" :inline="true">
            <el-form-item label="平台">
              <el-select v-model="transactionQuery.platform" clearable style="width: 130px">
                <el-option label="微信" value="WECHAT" />
                <el-option label="支付宝" value="ALIPAY" />
              </el-select>
            </el-form-item>
            <el-form-item label="交易状态">
              <el-select v-model="transactionQuery.status" clearable style="width: 150px">
                <el-option v-for="item in transactionStatuses" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="确认等级">
              <el-select v-model="transactionQuery.confirmationStatus" clearable style="width: 150px">
                <el-option v-for="item in confirmationStatuses" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="searchTransactions">查询</el-button>
              <el-button icon="Refresh" @click="resetTransactions">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="transactionLoading" :data="transactions" border>
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="交易 ID" prop="id" min-width="180" show-overflow-tooltip />
            <el-table-column label="平台" width="95" align="center">
              <template #default="{ row }">{{ platformLabel(row.platform) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }"><strong>{{ formatAmount(row.amountMinor) }}</strong></template>
            </el-table-column>
            <el-table-column label="交易状态" width="115" align="center">
              <template #default="{ row }">
                <el-tag :type="transactionType(row.status)">{{ transactionLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="确认等级" width="135" align="center">
              <template #default="{ row }">
                <el-tag :type="confirmationType(row.confirmationStatus)" effect="plain">
                  {{ confirmationLabel(row.confirmationStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="订单 ID" prop="orderId" min-width="175" show-overflow-tooltip />
            <el-table-column label="事件 ID" prop="eventId" min-width="175" show-overflow-tooltip />
            <el-table-column label="观察时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.observedAt) }}</template>
            </el-table-column>
            <el-table-column label="确认时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.confirmedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="190" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.orderId && row.confirmationStatus === 'NOTIFICATION'"
                  v-hasPermi="['payment:transaction:confirm']"
                  link
                  type="success"
                  @click="confirmTransaction(row as PaymentTransactionVO)"
                >
                  人工确认
                </el-button>
                <el-button
                  v-if="['MATCHED', 'CONFIRMED', 'RECONCILED'].includes(row.status)"
                  v-hasPermi="['payment:transaction:reverse']"
                  link
                  type="danger"
                  @click="requestReverse(row as PaymentTransactionVO)"
                >
                  申请撤销
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="transactionTotal > 0"
            v-model:page="transactionQuery.pageNum"
            v-model:limit="transactionQuery.pageSize"
            :total="transactionTotal"
            @pagination="loadTransactions"
          />
        </el-tab-pane>

        <el-tab-pane label="金额槽位" name="slots">
          <el-alert
            title="同一商户、平台和应付金额全局唯一；订单终态后进入 10 分钟冷却，防止迟到通知误匹配新订单。"
            type="info"
            :closable="false"
            show-icon
            class="mb-3"
          />
          <el-form :model="slotQuery" :inline="true">
            <el-form-item label="平台">
              <el-select v-model="slotQuery.platform" clearable style="width: 130px">
                <el-option label="微信" value="WECHAT" />
                <el-option label="支付宝" value="ALIPAY" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="slotQuery.status" clearable style="width: 140px">
                <el-option label="占用中" value="ACTIVE" />
                <el-option label="冷却中" value="COOLING" />
                <el-option label="已释放" value="RELEASED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="loadSlots">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="slotLoading" :data="slots" border>
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="平台" width="100">
              <template #default="{ row }">{{ platformLabel(row.platform) }}</template>
            </el-table-column>
            <el-table-column label="应付金额" width="130" align="right">
              <template #default="{ row }">{{ formatAmount(row.payableAmountMinor) }}</template>
            </el-table-column>
            <el-table-column label="订单 ID" prop="orderId" min-width="180" />
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'COOLING' ? 'warning' : 'info'">
                  {{ slotLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="占用时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.reservedAt) }}</template>
            </el-table-column>
            <el-table-column label="冷却至" width="190">
              <template #default="{ row }">{{ formatApiTime(row.coolingUntil) }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.updatedAt) }}</template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="slotTotal > 0"
            v-model:page="slotQuery.pageNum"
            v-model:limit="slotQuery.pageSize"
            :total="slotTotal"
            @pagination="loadSlots"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup name="PaymentTransaction" lang="ts">
import {
  confirmPaymentTransaction,
  listAmountSlots,
  listPaymentTransactions,
  reversePaymentTransaction
} from '@/api/payment';
import { formatApiTime } from '@/api/payment/time';
import type {
  AmountSlotQuery,
  AmountSlotVO,
  PaymentTransactionQuery,
  PaymentTransactionVO
} from '@/api/payment/types';
import { usePaymentMerchantScope } from '@/hooks/payment/useMerchantScope';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import { runMfaSensitiveOperation } from '@/views/payment/sensitive-operation/flow';

const activeTab = ref('transactions');
const transactionLoading = ref(false);
const transactionTotal = ref(0);
const transactions = ref<PaymentTransactionVO[]>([]);
const transactionQuery = ref<PaymentTransactionQuery>({ pageNum: 1, pageSize: 10 });
const slotLoading = ref(false);
const slotTotal = ref(0);
const slots = ref<AmountSlotVO[]>([]);
const slotQuery = ref<AmountSlotQuery>({ pageNum: 1, pageSize: 10 });
const {
  merchantStore,
  showMerchantColumn,
  watchScope
} = usePaymentMerchantScope();

const transactionStatuses = [
  { value: 'OBSERVED', label: '已观察' },
  { value: 'MATCHED', label: '已匹配' },
  { value: 'CONFIRMED', label: '人工已确认' },
  { value: 'RECONCILED', label: '内部已对账' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'REVERSED', label: '已撤销' }
];
const confirmationStatuses = [
  { value: 'UNCONFIRMED', label: '未确认' },
  { value: 'NOTIFICATION', label: '通知确认' },
  { value: 'MANUAL', label: '人工确认' },
  { value: 'RECONCILED', label: '内部已对账' }
];

const loadTransactions = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  transactionLoading.value = true;
  try {
    const response = await listPaymentTransactions(transactionQuery.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    transactions.value = response.data?.rows || [];
    transactionTotal.value = response.data?.total || 0;
  } finally {
    transactionLoading.value = false;
  }
};
const loadSlots = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  slotLoading.value = true;
  try {
    const response = await listAmountSlots(slotQuery.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    slots.value = response.data?.rows || [];
    slotTotal.value = response.data?.total || 0;
  } finally {
    slotLoading.value = false;
  }
};
const searchTransactions = () => {
  transactionQuery.value.pageNum = 1;
  loadTransactions();
};
const resetTransactions = () => {
  transactionQuery.value = { pageNum: 1, pageSize: transactionQuery.value.pageSize };
  loadTransactions();
};
const refresh = () => activeTab.value === 'slots' ? loadSlots() : loadTransactions();
const confirmTransaction = async (row: PaymentTransactionVO) => {
  const result = await ElMessageBox.prompt(
    `商户“${row.merchantName || row.merchantCode || row.merchantId}”的交易 ${row.id}：可填写本次人工确认备注`,
    '人工确认支付',
    {
      inputType: 'textarea',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    }
  );
  await confirmPaymentTransaction(row.id, result.value);
  ElMessage.success('交易已升级为人工确认');
  loadTransactions();
};
const requestReverse = async (row: PaymentTransactionVO) => {
  const result = await ElMessageBox.prompt(
    `商户“${row.merchantName || row.merchantCode || row.merchantId}”的交易 ${row.id}：撤销后订单将进入冲突状态，请填写原因`,
    '撤销支付确认',
    {
      inputType: 'textarea',
      inputValidator: value => Boolean(value?.trim()) || '请输入撤销原因',
      confirmButtonText: '继续验证',
      cancelButtonText: '取消'
    }
  );
  await runMfaSensitiveOperation(
    'PAYMENT_CONFIRMATION_REVERSE',
    '撤销支付确认',
    requestPaymentStepUp,
    token => reversePaymentTransaction(row.id, result.value, token)
  );
  ElMessage.success('支付确认已撤销');
  await loadTransactions();
};
const formatAmount = (minor?: number) => minor == null ? '-' : `¥${(minor / 100).toFixed(2)}`;
const platformLabel = (value: string) => value === 'WECHAT' ? '微信' : value === 'ALIPAY' ? '支付宝' : value;
const transactionLabel = (value: string) => transactionStatuses.find(item => item.value === value)?.label || value;
const confirmationLabel = (value: string) => confirmationStatuses.find(item => item.value === value)?.label || value;
const slotLabel = (value: string) => ({ ACTIVE: '占用中', COOLING: '冷却中', RELEASED: '已释放' })[value] || value;
const transactionType = (value: string): 'success' | 'warning' | 'danger' | 'info' =>
  value === 'RECONCILED' || value === 'CONFIRMED' ? 'success' : value === 'REVERSED' || value === 'REJECTED' ? 'danger' : value === 'MATCHED' ? 'warning' : 'info';
const confirmationType = (value: string): 'success' | 'warning' | 'info' =>
  value === 'RECONCILED' || value === 'MANUAL' ? 'success' : value === 'NOTIFICATION' ? 'warning' : 'info';

watch(activeTab, value => {
  if (value === 'slots' && slots.value.length === 0) loadSlots();
});
watchScope(async () => {
  transactionQuery.value.pageNum = 1;
  slotQuery.value.pageNum = 1;
  transactions.value = [];
  slots.value = [];
  await refresh();
});
onMounted(loadTransactions);
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  h3 { margin: 0 0 6px; }
  p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
}
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
