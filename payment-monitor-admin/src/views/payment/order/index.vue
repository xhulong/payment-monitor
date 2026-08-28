<template>
  <div class="p-2">
    <el-card shadow="hover">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="订单号" prop="merchantOrderNo">
          <el-input v-model="queryParams.merchantOrderNo" clearable placeholder="商户订单号" />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select v-model="queryParams.platform" clearable placeholder="全部平台" style="width: 130px">
            <el-option label="微信" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="等待付款" value="PENDING" />
            <el-option label="已支付" value="PAID" />
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="冲突" value="CONFLICT" />
          </el-select>
        </el-form-item>
        <el-form-item label="精确金额">
          <el-input-number v-model="queryAmountYuan" :min="0.01" :precision="2" :step="0.01" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="!merchantStore.canAccessAllMerchants && merchantBlocked"
      class="mt-3"
      type="warning"
      show-icon
      :closable="false"
      title="商户尚未完成开通，暂时不能创建动态金额订单"
    >
      <template #default>
        <div class="onboarding-warning">
          <span>请先完成：{{ pendingChecklistText }}</span>
          <el-button type="warning" link @click="goToOnboarding">前往商户入驻向导</el-button>
        </div>
      </template>
    </el-alert>

    <el-card shadow="hover" class="mt-3">
      <template #header>
        <div class="toolbar">
          <div>
            <h3>动态金额支付订单</h3>
            <p>每个待支付订单占用唯一分值，收入通知按平台、金额和时间窗口自动匹配。</p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-hasPermi="['payment:order:add']"
              type="primary"
              icon="Plus"
              :disabled="!merchantStore.canAccessAllMerchants && merchantBlocked"
              @click="openCreate"
            >
              创建订单
            </el-button>
            <el-button
              v-hasPermi="['payment:order:cancel']"
              type="danger"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchCancel"
            >
              批量取消
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" :selectable="row => row.status === 'PENDING'" />
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单号" prop="merchantOrderNo" min-width="200" show-overflow-tooltip />
        <el-table-column label="平台" width="95" align="center">
          <template #default="{ row }">{{ row.platform === 'WECHAT' ? '微信' : '支付宝' }}</template>
        </el-table-column>
        <el-table-column label="原金额" width="110" align="right">
          <template #default="{ row }">{{ formatAmount(row.requestedAmountMinor) }}</template>
        </el-table-column>
        <el-table-column label="应付金额" width="120" align="right">
          <template #default="{ row }"><strong>{{ formatAmount(row.payableAmountMinor) }}</strong></template>
        </el-table-column>
        <el-table-column label="分值偏移" width="90" align="center">
          <template #default="{ row }">+{{ row.amountOffsetMinor }}</template>
        </el-table-column>
        <el-table-column label="二维码" prop="qrAssetName" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认等级" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="confirmationType(row.confirmationStatus)" effect="plain">
              {{ confirmationLabel(row.confirmationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额槽位" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.amountSlotStatus" :type="row.amountSlotStatus === 'ACTIVE' ? 'success' : row.amountSlotStatus === 'COOLING' ? 'warning' : 'info'">
              {{ slotLabel(row.amountSlotStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="190">
          <template #default="{ row }">{{ formatApiTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" width="190">
          <template #default="{ row }">{{ formatApiTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="匹配事件" prop="matchedEventId" min-width="175" show-overflow-tooltip />
        <el-table-column label="操作" fixed="right" width="240" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button link type="success" @click="openPay(row as PaymentOrderVO)">支付页</el-button>
            <el-button
              v-if="row.status !== 'PAID' && row.status !== 'CANCELLED'"
              v-hasPermi="['payment:order:match']"
              link
              type="warning"
              @click="openMatch(row as PaymentOrderVO)"
            >
              补单
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              v-hasPermi="['payment:order:cancel']"
              link
              type="danger"
              @click="cancel(row as PaymentOrderVO)"
            >
              取消
            </el-button>
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

    <el-dialog v-model="createDialog.visible" title="创建动态金额订单" width="600px">
      <el-form :model="createDialog.form" label-width="110px">
        <el-form-item v-if="showMerchantColumn" label="目标商户" required>
          <payment-merchant-target-select
            v-model="createDialog.form.merchantId"
            active-only
            @update:model-value="loadAssets"
          />
        </el-form-item>
        <el-form-item label="平台" required>
          <el-radio-group v-model="createDialog.form.platform" @change="loadAssets">
            <el-radio-button value="WECHAT">微信</el-radio-button>
            <el-radio-button value="ALIPAY">支付宝</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="收款二维码" required>
          <el-select v-model="createDialog.form.qrAssetId" placeholder="选择启用的收款二维码" style="width: 100%">
            <el-option v-for="item in enabledAssets" :key="item.id" :label="item.assetName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单金额" required>
          <el-input-number v-model="createAmountYuan" :min="0.01" :max="999999.99" :precision="2" :step="0.01" />
          <span class="ml-2">元</span>
        </el-form-item>
        <el-form-item label="有效时间">
          <el-input-number v-model="createDialog.form.expiresSeconds" :min="60" :max="3600" :step="60" />
          <span class="ml-2">秒</span>
        </el-form-item>
        <el-form-item label="商户订单号">
          <el-input v-model="createDialog.form.merchantOrderNo" placeholder="留空自动生成" maxlength="64" />
        </el-form-item>
        <el-form-item label="订单标题">
          <el-input v-model="createDialog.form.subject" maxlength="200" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createDialog.form.customerNote" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="支付订单详情" size="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号" :span="2">{{ detail.merchantOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="平台">{{ detail.platform }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="原金额">{{ formatAmount(detail.requestedAmountMinor) }}</el-descriptions-item>
          <el-descriptions-item label="应付金额">{{ formatAmount(detail.payableAmountMinor) }}</el-descriptions-item>
          <el-descriptions-item label="分值偏移">+{{ detail.amountOffsetMinor }} 分</el-descriptions-item>
          <el-descriptions-item label="二维码">{{ detail.qrAssetName || detail.qrAssetId }}</el-descriptions-item>
          <el-descriptions-item label="匹配事件">{{ detail.matchedEventId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付交易">{{ detail.transactionId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认等级">{{ confirmationLabel(detail.confirmationStatus) }}</el-descriptions-item>
          <el-descriptions-item label="确认来源">{{ detail.confirmationSource || '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatApiTime(detail.confirmedAt) }}</el-descriptions-item>
          <el-descriptions-item label="金额槽位">
            {{ slotLabel(detail.amountSlotStatus || '') }}
            <span v-if="detail.amountSlotCoolingUntil">，冷却至 {{ formatApiTime(detail.amountSlotCoolingUntil) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatApiTime(detail.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="支付页" :span="2">
            <el-link type="primary" :href="detail.payUrl" target="_blank">{{ detail.payUrl }}</el-link>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.matchHistory || []" border class="mt-4">
          <el-table-column label="时间" width="190">
            <template #default="{ row }">{{ formatApiTime(row.operatedAt) }}</template>
          </el-table-column>
          <el-table-column label="动作" prop="action" width="130" />
          <el-table-column label="状态变化" min-width="170">
            <template #default="{ row }">{{ row.beforeStatus || '-' }} → {{ row.afterStatus || '-' }}</template>
          </el-table-column>
          <el-table-column label="事件 ID" prop="eventId" min-width="170" />
          <el-table-column label="备注" prop="note" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog
      v-model="matchDialog.visible"
      :title="`人工补单 · ${matchDialog.order?.merchantName || matchDialog.order?.merchantCode || matchDialog.order?.merchantId || '-'}`"
      width="760px"
    >
      <el-form :model="matchDialog.form" label-width="100px">
        <el-form-item label="支付事件" required>
          <el-select
            v-model="matchDialog.form.eventId"
            filterable
            :loading="matchDialog.loadingCandidates"
            placeholder="选择可用于当前订单的收入事件"
            no-data-text="暂无可用支付事件"
            style="width: 100%"
            @change="handleMatchCandidateChange"
          >
            <el-option-group
              v-for="group in matchCandidateGroups"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="candidate in group.candidates"
                :key="candidate.id"
                :value="candidate.id"
                :label="matchCandidateOptionLabel(candidate)"
              >
                <div class="match-candidate-option">
                  <span class="candidate-id">
                    #{{ candidate.id }}
                    <small>{{ candidate.clientEventId || '无客户端事件 ID' }}</small>
                  </span>
                  <span>{{ platformLabel(candidate.platform) }}</span>
                  <strong>{{ formatAmount(candidate.amountMinor) }}</strong>
                  <span>{{ formatApiTime(candidate.eventTime || candidate.receivedAt) }}</span>
                  <el-tag
                    size="small"
                    :type="candidate.exactMatch ? 'success' : 'warning'"
                  >
                    {{ candidate.exactMatch ? '精确匹配' : '需强制匹配' }}
                  </el-tag>
                </div>
              </el-option>
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-alert
          v-if="selectedMatchCandidate"
          class="match-candidate-alert"
          :type="selectedMatchCandidate.exactMatch ? 'success' : 'warning'"
          :closable="false"
          show-icon
          :title="
            selectedMatchCandidate.exactMatch
              ? '平台和应付金额一致，本次补单无需敏感操作确认'
              : `${matchCandidateDifference(selectedMatchCandidate, matchDialog.order)}，必须开启强制匹配并完成敏感操作确认`
          "
        />
        <el-form-item
          v-if="selectedMatchCandidate && !selectedMatchCandidate.exactMatch"
          label="强制匹配"
        >
          <el-switch v-model="matchDialog.form.force" />
          <span class="ml-2">我已核对差异并确认强制匹配</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="matchDialog.form.note" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="matchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="matchDialog.submitting" @click="submitMatch">确认补单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PaymentOrder" lang="ts">
import {
  batchCancelPaymentOrders,
  cancelPaymentOrder,
  createPaymentOrder,
  getMerchantOnboardingStatus,
  getPaymentOrder,
  listEnabledQrAssets,
  listPaymentOrderMatchCandidates,
  listPaymentOrders,
  manualMatchPaymentOrder
} from '@/api/payment';
import type {
  ManualOrderMatchForm,
  MerchantOnboardingStatusVO,
  OrderMatchCandidateVO,
  PaymentOrderCreateForm,
  PaymentOrderQuery,
  PaymentOrderVO,
  QrAssetVO
} from '@/api/payment/types';
import { formatApiTime } from '@/api/payment/time';
import PaymentMerchantTargetSelect from '@/components/PaymentMerchantTargetSelect/index.vue';
import {
  hasMixedMerchantSelection,
  usePaymentMerchantScope
} from '@/hooks/payment/useMerchantScope';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import { runOptionalMfaOperation } from '@/views/payment/sensitive-operation/flow';
import {
  groupMatchCandidates,
  matchCandidateDifference,
  validateMatchCandidateSelection
} from './match-candidates';
import {
  isNavigableRouteMatch,
  PAYMENT_ROUTES
} from '@/utils/payment-routes';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const router = useRouter();
const loading = ref(false);
const total = ref(0);
const rows = ref<PaymentOrderVO[]>([]);
const { ids, selectedRows, multiple, handleSelectionChange, clearSelection } =
  useTableSelection<PaymentOrderVO>(item => item.id);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();
const mixedMerchantSelection = computed(() =>
  hasMixedMerchantSelection(selectedRows.value)
);
const detail = ref<PaymentOrderVO>();
const detailVisible = ref(false);
const enabledAssets = ref<QrAssetVO[]>([]);
const queryFormRef = ref();
const queryAmountYuan = ref<number>();
const createAmountYuan = ref<number>(1);
const onboardingStatus = ref<MerchantOnboardingStatusVO>();
const merchantBlocked = computed(
  () =>
    onboardingStatus.value?.onboardingAvailable !== false &&
    Boolean(onboardingStatus.value?.merchantId) &&
    onboardingStatus.value?.merchantLifecycle !== 'ACTIVE'
);
const pendingChecklist = computed(
  () => onboardingStatus.value?.checklist?.filter(item => item.required && !item.completed) || []
);
const pendingChecklistText = computed(
  () => pendingChecklist.value.map(item => item.label).join('、') || '商户开通清单'
);
const queryParams = ref<PaymentOrderQuery>({ pageNum: 1, pageSize: 10 });
const createDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  form: PaymentOrderCreateForm;
}>({
  visible: false,
  submitting: false,
  form: { platform: 'WECHAT', qrAssetId: '', amountMinor: 100, expiresSeconds: 300 }
});
const matchDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  loadingCandidates: boolean;
  orderId?: string | number;
  order?: PaymentOrderVO;
  candidates: OrderMatchCandidateVO[];
  form: ManualOrderMatchForm;
}>({
  visible: false,
  submitting: false,
  loadingCandidates: false,
  candidates: [],
  form: { eventId: '', force: false, note: '' }
});
const selectedMatchCandidate = computed(() =>
  matchDialog.candidates.find(
    candidate => String(candidate.id) === String(matchDialog.form.eventId)
  )
);
const matchCandidateGroups = computed(() =>
  groupMatchCandidates(matchDialog.candidates)
);

const getList = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    queryParams.value.payableAmountMinor =
      queryAmountYuan.value == null ? undefined : Math.round(queryAmountYuan.value * 100);
    const response = await listPaymentOrders(queryParams.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    rows.value = response.data?.rows || [];
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
  queryAmountYuan.value = undefined;
  queryParams.value = { pageNum: 1, pageSize: queryParams.value.pageSize };
  getList();
};
const loadAssets = async () => {
  createDialog.form.qrAssetId = '';
  if (!createDialog.form.merchantId) {
    enabledAssets.value = [];
    return;
  }
  enabledAssets.value =
    (
      await listEnabledQrAssets(
        createDialog.form.platform,
        createDialog.form.merchantId
      )
    ).data || [];
};
const loadOnboardingState = async () => {
  if (merchantStore.canAccessAllMerchants) {
    onboardingStatus.value = undefined;
    return;
  }
  const response = await getMerchantOnboardingStatus();
  onboardingStatus.value = response.data;
};
const goToOnboarding = async () => {
  const resolved = router.resolve(PAYMENT_ROUTES.onboarding);
  if (!isNavigableRouteMatch(resolved.matched.map(item => item.path))) {
    ElMessage.warning('商户入驻向导暂时不可用，请从左侧菜单进入');
    return;
  }
  try {
    await router.push(resolved.fullPath);
  } catch {
    ElMessage.warning('商户入驻向导打开失败，请稍后重试');
  }
};
const openCreate = async () => {
  if (!merchantStore.context) await merchantStore.load();
  await loadOnboardingState();
  if (merchantBlocked.value) {
    await ElMessageBox.alert(
      `商户尚未完成开通，请先完成：${pendingChecklistText.value}`,
      '暂时不能创建订单',
      { type: 'warning', confirmButtonText: '前往开通向导' }
    );
    await goToOnboarding();
    return;
  }
  createDialog.form = {
    merchantId: defaultTargetMerchantId(),
    platform: 'WECHAT',
    qrAssetId: '',
    amountMinor: 100,
    expiresSeconds: 300,
    merchantOrderNo: '',
    subject: '',
    customerNote: ''
  };
  createAmountYuan.value = 1;
  await loadAssets();
  createDialog.visible = true;
};
const submitCreate = async () => {
  if (!createDialog.form.merchantId) {
    proxy?.$modal.msgWarning('请选择目标商户');
    return;
  }
  if (!createDialog.form.qrAssetId || !createAmountYuan.value) {
    proxy?.$modal.msgWarning('请选择二维码并填写金额');
    return;
  }
  createDialog.form.amountMinor = Math.round(createAmountYuan.value * 100);
  createDialog.submitting = true;
  try {
    const order = (await createPaymentOrder(createDialog.form)).data;
    createDialog.visible = false;
    proxy?.$modal.msgSuccess(`订单已创建，应付 ${formatAmount(order.payableAmountMinor)}`);
    window.open(order.payUrl, '_blank', 'noopener');
    getList();
  } finally {
    createDialog.submitting = false;
  }
};
const showDetail = async (id: string | number) => {
  detail.value = (await getPaymentOrder(id)).data;
  detailVisible.value = true;
};
const openPay = (order: PaymentOrderVO) => window.open(order.payUrl, '_blank', 'noopener');
const cancel = async (order: PaymentOrderVO) => {
  await proxy?.$modal.confirm(
    `确认取消商户“${order.merchantName || order.merchantCode || order.merchantId}”的订单“${order.merchantOrderNo}”吗？`
  );
  await cancelPaymentOrder(order.id, '管理端取消');
  proxy?.$modal.msgSuccess('订单已取消');
  getList();
};
const batchCancel = async () => {
  if (mixedMerchantSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  const { value } = await ElMessageBox.prompt(
    `确认取消选中的 ${ids.value.length} 个待支付订单吗？`,
    '批量取消订单',
    {
      inputPlaceholder: '可填写取消原因',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputValidator: value => !value || value.length <= 500 || '取消原因不能超过 500 个字符'
    }
  );
  await batchCancelPaymentOrders(ids.value, value);
  proxy?.$modal.msgSuccess('批量取消成功');
  getList();
};
const loadMatchCandidates = async () => {
  if (!matchDialog.orderId) return;
  matchDialog.loadingCandidates = true;
  try {
    const response = await listPaymentOrderMatchCandidates(matchDialog.orderId);
    matchDialog.candidates = response.data || [];
    if (
      matchDialog.form.eventId &&
      !matchDialog.candidates.some(
        candidate => String(candidate.id) === String(matchDialog.form.eventId)
      )
    ) {
      matchDialog.form.eventId = '';
      matchDialog.form.force = false;
    }
  } finally {
    matchDialog.loadingCandidates = false;
  }
};
const openMatch = async (order: PaymentOrderVO) => {
  matchDialog.orderId = order.id;
  matchDialog.order = order;
  matchDialog.candidates = [];
  matchDialog.form = { eventId: '', force: false, note: '' };
  matchDialog.visible = true;
  await loadMatchCandidates();
};
const handleMatchCandidateChange = () => {
  if (selectedMatchCandidate.value?.exactMatch) {
    matchDialog.form.force = false;
  }
};
const submitMatch = async () => {
  if (!matchDialog.orderId) return;
  const validationMessage = validateMatchCandidateSelection(
    selectedMatchCandidate.value,
    matchDialog.form.force
  );
  if (validationMessage) {
    proxy?.$modal.msgWarning(validationMessage);
    return;
  }
  if (selectedMatchCandidate.value?.exactMatch) {
    matchDialog.form.force = false;
  }
  matchDialog.submitting = true;
  try {
    await runOptionalMfaOperation(
      matchDialog.form.force,
      'PAYMENT_ORDER_FORCE_MATCH',
      '强制补单确认',
      requestPaymentStepUp,
      stepUpToken =>
        manualMatchPaymentOrder(
          matchDialog.orderId!,
          matchDialog.form,
          stepUpToken
        )
    );
    matchDialog.visible = false;
    proxy?.$modal.msgSuccess(
      matchDialog.form.force ? '强制补单已完成' : '补单完成'
    );
    await getList();
  } catch {
    await loadMatchCandidates();
    proxy?.$modal.msgWarning('补单未完成，候选列表已刷新，请重新确认');
  } finally {
    matchDialog.submitting = false;
  }
};
const platformLabel = (platform: string) =>
  ({ WECHAT: '微信', ALIPAY: '支付宝' })[platform] || platform;
const matchCandidateOptionLabel = (candidate: OrderMatchCandidateVO) =>
  [
    candidate.id,
    candidate.clientEventId,
    platformLabel(candidate.platform),
    formatAmount(candidate.amountMinor),
    formatApiTime(candidate.eventTime || candidate.receivedAt),
    candidate.exactMatch ? '精确匹配' : '需强制匹配'
  ].filter(Boolean).join(' · ');
const formatAmount = (minor?: number) => (minor == null ? '-' : `¥${(minor / 100).toFixed(2)}`);
const statusLabel = (status: string) =>
  ({ PENDING: '等待付款', PAID: '已支付', EXPIRED: '已过期', CANCELLED: '已取消', CONFLICT: '冲突' })[
    status
  ] || status;
const statusType = (status: string): 'success' | 'warning' | 'danger' | 'info' =>
  status === 'PAID' ? 'success' : status === 'PENDING' ? 'warning' : status === 'CONFLICT' ? 'danger' : 'info';
const confirmationLabel = (status?: string) =>
  ({ UNCONFIRMED: '未确认', NOTIFICATION: '通知确认', MANUAL: '人工确认', RECONCILED: '内部已对账' })[
    status || ''
  ] || status || '-';
const confirmationType = (status?: string): 'success' | 'warning' | 'info' =>
  status === 'MANUAL' || status === 'RECONCILED' ? 'success' : status === 'NOTIFICATION' ? 'warning' : 'info';
const slotLabel = (status?: string) =>
  ({ ACTIVE: '占用中', COOLING: '冷却中', RELEASED: '已释放' })[status || ''] || status || '-';

watchScope(async () => {
  queryParams.value.pageNum = 1;
  detailVisible.value = false;
  createDialog.visible = false;
  matchDialog.visible = false;
  clearSelection();
  await getList();
});
onMounted(async () => {
  if (!merchantStore.context) await merchantStore.load();
  await Promise.all([loadOnboardingState(), getList()]);
});
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  h3 { margin: 0 0 5px; }
  p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
}
.onboarding-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.toolbar-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }
.match-candidate-alert {
  width: calc(100% - 100px);
  margin: 0 0 18px 100px;
}
.match-candidate-option {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  .candidate-id {
    display: inline-flex;
    min-width: 190px;
    flex-direction: column;
    line-height: 1.2;
    small {
      max-width: 180px;
      overflow: hidden;
      color: var(--el-text-color-secondary);
      text-overflow: ellipsis;
    }
  }
}
</style>
