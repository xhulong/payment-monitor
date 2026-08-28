<template>
  <div class="p-2">
    <el-card shadow="hover">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="平台" prop="platform">
          <el-select v-model="queryParams.platform" clearable placeholder="全部平台" style="width: 130px">
            <el-option label="微信" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-select v-model="queryParams.direction" clearable placeholder="全部方向" style="width: 130px">
            <el-option label="收入" value="INCOME" />
            <el-option label="支出" value="EXPENSE" />
            <el-option label="未知" value="UNKNOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务状态" prop="status">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="待审核" value="RECEIVED" />
            <el-option label="已审核" value="REVIEWED" />
            <el-option label="已忽略" value="IGNORED" />
            <el-option label="已匹配" value="MATCHED" />
            <el-option label="冲突" value="CONFLICT" />
          </el-select>
        </el-form-item>
        <el-form-item label="解析状态" prop="parseStatus">
          <el-select v-model="queryParams.parseStatus" clearable placeholder="全部解析状态" style="width: 160px">
            <el-option label="解析成功" value="PARSED" />
            <el-option label="未找到金额" value="AMOUNT_NOT_FOUND" />
            <el-option label="方向冲突" value="AMBIGUOUS" />
          </el-select>
        </el-form-item>
        <el-form-item label="重复状态" prop="duplicateStatus">
          <el-select v-model="queryParams.duplicateStatus" clearable placeholder="全部" style="width: 140px">
            <el-option label="疑似重复" value="SUSPECTED" />
            <el-option label="已确认重复" value="CONFIRMED" />
            <el-option label="已排除" value="EXCLUDED" />
            <el-option label="非重复" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" clearable placeholder="事件ID、规则或哈希" style="width: 220px" />
        </el-form-item>
        <el-form-item label="设备 ID" prop="deviceId">
          <el-input v-model="queryParams.deviceId" clearable placeholder="设备 ID" style="width: 170px" />
        </el-form-item>
        <el-form-item label="金额区间">
          <el-input-number v-model="minAmountYuan" :min="0.01" :precision="2" :step="0.01" controls-position="right" />
          <span class="amount-separator">-</span>
          <el-input-number v-model="maxAmountYuan" :min="0.01" :precision="2" :step="0.01" controls-position="right" />
        </el-form-item>
        <el-form-item label="接收时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="mt-3">
      <template #header>
        <div class="toolbar">
          <div>
            <h3>支付通知事件</h3>
            <p>支持人工确认、方向/金额修正、忽略、原文分级查看和 Excel 导出。</p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['payment:event:export']" icon="Download" @click="handleExport">导出</el-button>
            <el-button
              v-hasPermi="['payment:event:review']"
              type="success"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchReview('REVIEW')"
            >
              批量确认
            </el-button>
            <el-button
              v-hasPermi="['payment:event:review']"
              type="warning"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchReview('IGNORE')"
            >
              批量忽略
            </el-button>
            <el-button icon="Refresh" @click="getList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="eventList" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" :selectable="row => row.status === 'RECEIVED'" />
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="平台" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'WECHAT' ? 'success' : 'primary'">
              {{ row.platform === 'WECHAT' ? '微信' : '支付宝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="directionType(row.direction)">{{ directionLabel(row.direction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amountMinor, row.currency) }}</template>
        </el-table-column>
        <el-table-column label="业务状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="eventStatusType(row.status)">{{ eventStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="解析状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="parseStatusType(row.parseStatus)">{{ parseStatusLabel(row.parseStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重复识别" width="115" align="center">
          <template #default="{ row }">
            <el-tag :type="duplicateStatusType(row.duplicateStatus)">
              {{ duplicateStatusLabel(row.duplicateStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配规则" prop="matchedRule" min-width="190" show-overflow-tooltip />
        <el-table-column label="通知时间" width="190">
          <template #default="{ row }">{{ formatApiTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column label="服务端接收" width="190">
          <template #default="{ row }">{{ formatApiTime(row.receivedAt) }}</template>
        </el-table-column>
        <el-table-column label="同步耗时" width="105" align="right">
          <template #default="{ row }">{{ formatSyncLatency(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="245" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button
              v-hasPermi="['payment:event:review']"
              link
              type="success"
              @click="openReview(row as PaymentEventVO)"
            >
              审核
            </el-button>
            <el-dropdown
              v-if="row.duplicateStatus === 'SUSPECTED'"
              v-hasPermi="['payment:event:duplicate']"
              @command="status => reviewDuplicate(row as PaymentEventVO, status)"
            >
              <el-button link type="warning">重复处理</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="CONFIRMED">确认重复</el-dropdown-item>
                  <el-dropdown-item command="EXCLUDED">排除重复</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="支付事件详情" size="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="事件 ID" :span="2">{{ detail.clientEventId }}</el-descriptions-item>
          <el-descriptions-item label="平台">{{ detail.platform }}</el-descriptions-item>
          <el-descriptions-item label="方向">{{ directionLabel(detail.direction) }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatAmount(detail.amountMinor, detail.currency) }}</el-descriptions-item>
          <el-descriptions-item label="业务状态">{{ eventStatusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="解析状态">{{ parseStatusLabel(detail.parseStatus) }}</el-descriptions-item>
          <el-descriptions-item label="设备 ID">{{ detail.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="解析器版本">{{ detail.parserVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="通知时间">{{ formatApiTime(detail.eventTime) }}</el-descriptions-item>
          <el-descriptions-item label="App 捕获时间">{{ formatApiTime(detail.clientReceivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="App 发送时间">{{ formatApiTime(detail.clientSentAt) }}</el-descriptions-item>
          <el-descriptions-item label="服务端接收时间">{{ formatApiTime(detail.receivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="端到端同步耗时">{{ formatSyncLatency(detail) }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatApiTime(detail.reviewedAt) }}</el-descriptions-item>
          <el-descriptions-item label="审核人 ID">{{ detail.reviewedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核备注" :span="2">{{ detail.reviewNote || '-' }}</el-descriptions-item>
          <el-descriptions-item label="重复状态">{{ duplicateStatusLabel(detail.duplicateStatus) }}</el-descriptions-item>
          <el-descriptions-item label="关联首条事件">{{ detail.duplicateOfEventId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="重复识别时间">{{ formatApiTime(detail.duplicateDetectedAt) }}</el-descriptions-item>
          <el-descriptions-item label="重复审核时间">{{ formatApiTime(detail.duplicateReviewedAt) }}</el-descriptions-item>
          <el-descriptions-item label="重复审核备注" :span="2">{{ detail.duplicateReviewNote || '-' }}</el-descriptions-item>
          <el-descriptions-item label="匹配规则" :span="2">{{ detail.matchedRule || '-' }}</el-descriptions-item>
          <el-descriptions-item label="指纹" :span="2">{{ detail.fingerprint }}</el-descriptions-item>
          <el-descriptions-item label="原始数据哈希" :span="2">{{ detail.rawHash || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-card shadow="never" class="mt-4">
          <template #header>
            <div class="raw-header">
              <span>原始通知 JSON</span>
              <div>
                <el-button v-hasPermi="['payment:event:raw']" size="small" @click="loadRaw(false)">
                  查看脱敏原文
                </el-button>
                <el-button v-hasPermi="['payment:event:raw:full']" size="small" type="danger" @click="loadRaw(true)">
                  查看完整原文
                </el-button>
              </div>
            </div>
          </template>
          <pre class="raw-json">{{ formattedRawPayload }}</pre>
        </el-card>

        <el-card shadow="never" class="mt-4">
          <template #header>审核历史</template>
          <el-table :data="detail.reviewHistory || []" border>
            <el-table-column label="时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.operatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" prop="action" width="100" />
            <el-table-column label="状态变化" min-width="170">
              <template #default="{ row }">{{ row.beforeStatus }} → {{ row.afterStatus }}</template>
            </el-table-column>
            <el-table-column label="方向/金额变化" min-width="220">
              <template #default="{ row }">
                {{ row.beforeDirection || '-' }} / {{ formatAmount(row.beforeAmountMinor) }}
                →
                {{ row.afterDirection || '-' }} / {{ formatAmount(row.afterAmountMinor) }}
              </template>
            </el-table-column>
            <el-table-column label="备注" prop="note" min-width="180" show-overflow-tooltip />
          </el-table>
        </el-card>
      </template>
    </el-drawer>

    <el-dialog
      v-model="reviewDialog.visible"
      :title="`审核支付事件 · ${reviewDialog.merchantLabel || '-'}`"
      width="520px"
    >
      <el-form :model="reviewDialog.form" label-width="92px">
        <el-form-item label="操作">
          <el-radio-group v-model="reviewDialog.form.action">
            <el-radio-button value="REVIEW">确认</el-radio-button>
            <el-radio-button value="CORRECT">修正</el-radio-button>
            <el-radio-button value="IGNORE">忽略</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="reviewDialog.form.action === 'CORRECT'">
          <el-form-item label="修正方向">
            <el-select v-model="reviewDialog.form.direction" placeholder="保持原方向" clearable>
              <el-option label="收入" value="INCOME" />
              <el-option label="支出" value="EXPENSE" />
              <el-option label="未知" value="UNKNOWN" />
            </el-select>
          </el-form-item>
          <el-form-item label="修正金额">
            <el-input-number
              v-model="reviewAmountYuan"
              :min="0.01"
              :precision="2"
              :step="0.01"
              controls-position="right"
            />
            <span class="ml-2">元</span>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="reviewDialog.form.note" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="reviewDialog.submitting" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PaymentEvent" lang="ts">
import {
  batchReviewPaymentEvents,
  getPaymentEvent,
  getPaymentEventRaw,
  listPaymentEvents,
  reviewPaymentEvent,
  reviewDuplicatePaymentEvent
} from '@/api/payment';
import type {
  PaymentEventQuery,
  PaymentEventReviewForm,
  PaymentEventVO
} from '@/api/payment/types';
import { formatApiTime, toUtcIso } from '@/api/payment/time';
import { download as requestDownload } from '@/utils/request';
import {
  hasMixedMerchantSelection,
  usePaymentMerchantScope
} from '@/hooks/payment/useMerchantScope';
import { useTableSelection } from '@/hooks/table/useTableSelection';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const loading = ref(false);
const total = ref(0);
const eventList = ref<PaymentEventVO[]>([]);
const { ids, selectedRows, multiple, handleSelectionChange, clearSelection } =
  useTableSelection<PaymentEventVO>(item => item.id);
const {
  merchantStore,
  showMerchantColumn,
  watchScope
} = usePaymentMerchantScope();
const mixedMerchantSelection = computed(() =>
  hasMixedMerchantSelection(selectedRows.value)
);
const detail = ref<PaymentEventVO>();
const detailVisible = ref(false);
const rawPayload = ref<string>();
const queryFormRef = ref();
const dateRange = ref<[Date, Date]>();
const minAmountYuan = ref<number>();
const maxAmountYuan = ref<number>();
const reviewAmountYuan = ref<number>();
const queryParams = ref<PaymentEventQuery>({ pageNum: 1, pageSize: 10 });
const reviewDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  eventId?: string | number;
  merchantLabel?: string;
  form: PaymentEventReviewForm;
}>({
  visible: false,
  submitting: false,
  form: { action: 'REVIEW', note: '' }
});

const formattedRawPayload = computed(() => {
  if (!rawPayload.value) return '原文上传默认关闭，或尚未请求查看。';
  try {
    return JSON.stringify(JSON.parse(rawPayload.value), null, 2);
  } catch {
    return rawPayload.value;
  }
});

const prepareQuery = () => {
  queryParams.value.minAmountMinor =
    minAmountYuan.value == null ? undefined : Math.round(minAmountYuan.value * 100);
  queryParams.value.maxAmountMinor =
    maxAmountYuan.value == null ? undefined : Math.round(maxAmountYuan.value * 100);
  queryParams.value.beginTime = toUtcIso(dateRange.value?.[0]);
  queryParams.value.endTime = toUtcIso(dateRange.value?.[1]);
  return queryParams.value;
};

const getList = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listPaymentEvents(prepareQuery());
    if (scopeVersion !== merchantStore.scopeVersion) return;
    eventList.value = response.data?.rows || [];
    total.value = response.data?.total || 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryFormRef.value?.resetFields();
  minAmountYuan.value = undefined;
  maxAmountYuan.value = undefined;
  dateRange.value = undefined;
  queryParams.value = { pageNum: 1, pageSize: queryParams.value.pageSize };
  getList();
};

const handleExport = () => {
  requestDownload(
    '/payment/events/export',
    prepareQuery(),
    `payment_events_${new Date().getTime()}.xlsx`
  );
};

const showDetail = async (id: string | number) => {
  detail.value = (await getPaymentEvent(id)).data;
  rawPayload.value = undefined;
  detailVisible.value = true;
};

const loadRaw = async (full: boolean) => {
  if (!detail.value) return;
  const response = await getPaymentEventRaw(detail.value.id, full);
  rawPayload.value = response.data.rawPayload;
};

const openReview = (event: PaymentEventVO) => {
  reviewDialog.eventId = event.id;
  reviewDialog.merchantLabel =
    event.merchantName || event.merchantCode || String(event.merchantId);
  reviewDialog.form = {
    action: 'REVIEW',
    direction: event.direction,
    note: ''
  };
  reviewAmountYuan.value =
    event.amountMinor == null ? undefined : event.amountMinor / 100;
  reviewDialog.visible = true;
};

const submitReview = async () => {
  if (!reviewDialog.eventId) return;
  const form: PaymentEventReviewForm = { ...reviewDialog.form };
  if (form.action !== 'CORRECT') {
    delete form.direction;
    delete form.amountMinor;
  } else {
    form.amountMinor =
      reviewAmountYuan.value == null ? undefined : Math.round(reviewAmountYuan.value * 100);
    if (!form.direction && form.amountMinor == null) {
      proxy?.$modal.msgWarning('修正操作至少填写方向或金额');
      return;
    }
  }
  reviewDialog.submitting = true;
  try {
    const response = await reviewPaymentEvent(reviewDialog.eventId, form);
    reviewDialog.visible = false;
    proxy?.$modal.msgSuccess('审核完成');
    if (detail.value?.id === reviewDialog.eventId) detail.value = response.data;
    getList();
  } finally {
    reviewDialog.submitting = false;
  }
};

const reviewDuplicate = async (
  event: PaymentEventVO,
  status: 'CONFIRMED' | 'EXCLUDED'
) => {
  const title = status === 'CONFIRMED' ? '确认重复通知' : '排除重复标记';
  const merchantLabel =
    event.merchantName || event.merchantCode || String(event.merchantId);
  const result = await ElMessageBox.prompt(
    `商户“${merchantLabel}”的事件 ${event.id}：可填写处理备注`,
    title,
    {
      inputType: 'textarea',
      inputPlaceholder: '选填'
    }
  );
  await reviewDuplicatePaymentEvent(event.id, { status, note: result.value });
  proxy?.$modal.msgSuccess('重复状态已更新');
  await getList();
  if (detail.value?.id === event.id) {
    detail.value = (await getPaymentEvent(event.id)).data;
  }
};

const batchReview = async (action: 'REVIEW' | 'IGNORE') => {
  if (mixedMerchantSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  const actionLabel = action === 'REVIEW' ? '确认' : '忽略';
  const merchantLabel =
    selectedRows.value[0]?.merchantName ||
    selectedRows.value[0]?.merchantCode ||
    selectedRows.value[0]?.merchantId;
  const { value } = await ElMessageBox.prompt(
    `将商户“${merchantLabel || '-'}”的 ${ids.value.length} 条待审核事件批量${actionLabel}`,
    `批量${actionLabel}`,
    {
      inputType: 'textarea',
      inputPlaceholder: '可填写处理备注',
      inputValidator: value => !value || value.length <= 500 || '备注不能超过 500 个字符'
    }
  );
  await batchReviewPaymentEvents(ids.value, action, value);
  proxy?.$modal.msgSuccess(`批量${actionLabel}完成`);
  getList();
};

const directionLabel = (direction?: PaymentEventVO['direction'] | string) =>
  ({ INCOME: '收入', EXPENSE: '支出', UNKNOWN: '未知' })[direction || 'UNKNOWN'];
const directionType = (direction: PaymentEventVO['direction']): 'success' | 'warning' | 'info' =>
  direction === 'INCOME' ? 'success' : direction === 'EXPENSE' ? 'warning' : 'info';
const parseStatusLabel = (status: PaymentEventVO['parseStatus']) =>
  ({ PARSED: '解析成功', AMOUNT_NOT_FOUND: '未找到金额', AMBIGUOUS: '方向冲突' })[status];
const parseStatusType = (status: PaymentEventVO['parseStatus']): 'success' | 'warning' | 'info' =>
  status === 'PARSED' ? 'success' : status === 'AMOUNT_NOT_FOUND' ? 'warning' : 'info';
const eventStatusLabel = (status: string) =>
  ({ RECEIVED: '待审核', REVIEWED: '已审核', IGNORED: '已忽略', MATCHED: '已匹配', CONFLICT: '冲突' })[
    status
  ] || status;
const eventStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' =>
  status === 'REVIEWED' || status === 'MATCHED'
    ? 'success'
    : status === 'CONFLICT'
      ? 'danger'
      : status === 'RECEIVED'
        ? 'warning'
        : 'info';
const duplicateStatusLabel = (status?: PaymentEventVO['duplicateStatus'] | string) =>
  ({
    NONE: '非重复',
    SUSPECTED: '疑似重复',
    CONFIRMED: '已确认',
    EXCLUDED: '已排除'
  })[status || 'NONE'] || status;
const duplicateStatusType = (
  status?: PaymentEventVO['duplicateStatus']
): 'success' | 'warning' | 'danger' | 'info' =>
  status === 'SUSPECTED'
    ? 'warning'
    : status === 'CONFIRMED'
      ? 'danger'
      : status === 'EXCLUDED'
        ? 'success'
        : 'info';
const formatAmount = (amountMinor?: number, currency = 'CNY') =>
  amountMinor == null ? '-' : `${currency === 'CNY' ? '¥' : `${currency} `}${(amountMinor / 100).toFixed(2)}`;
const formatSyncLatency = (event: Partial<PaymentEventVO>) => {
  if (!event.receivedAt || event.clientReceivedAtMs == null) return '-';
  return `${Math.max(0, new Date(event.receivedAt).getTime() - event.clientReceivedAtMs)} ms`;
};

watchScope(async () => {
  queryParams.value.pageNum = 1;
  detailVisible.value = false;
  reviewDialog.visible = false;
  rawPayload.value = undefined;
  clearSelection();
  await getList();
});
onMounted(getList);
</script>

<style scoped lang="scss">
.toolbar,
.raw-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar {
  h3 {
    margin: 0 0 5px;
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}
.toolbar-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }

.amount-separator {
  padding: 0 8px;
}

.raw-json {
  overflow: auto;
  max-height: 480px;
  margin: 0;
  padding: 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
