<template>
  <div class="p-2">
    <el-alert type="info" :closable="false" show-icon>
      <template #title>可靠支付回调</template>
      订单支付成功后先写入 Outbox，再异步投递。签名原文为
      <el-text tag="code">timestamp + "." + exactBody</el-text>，
      请求头包含稳定 eventId、deliveryId、时间戳和 HMAC-SHA256 签名。
    </el-alert>

    <el-card shadow="hover" class="mt-3">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="回调端点" name="endpoints">
          <el-form ref="endpointQueryRef" :model="endpointQuery" :inline="true">
            <el-form-item label="名称" prop="endpointName">
              <el-input
                v-model="endpointQuery.endpointName"
                clearable
                placeholder="端点名称"
                @keyup.enter="searchEndpoints"
              />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="endpointQuery.status" clearable placeholder="全部状态" style="width: 130px">
                <el-option label="启用" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="searchEndpoints">搜索</el-button>
              <el-button icon="Refresh" @click="resetEndpointQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="section-toolbar">
            <div>
              <h3>Webhook 端点</h3>
              <p>密钥只在创建或轮换时展示一次；停用端点不会接收新回调。</p>
            </div>
            <div class="toolbar-actions">
              <el-button
                v-hasPermi="['payment:webhook:add']"
                type="primary"
                icon="Plus"
                @click="openCreateEndpoint"
              >
                新增端点
              </el-button>
              <el-button v-hasPermi="['payment:webhook:edit']" type="success" plain :disabled="endpointMultiple || mixedEndpointSelection" @click="batchEndpointStatus('0')">批量启用</el-button>
              <el-button v-hasPermi="['payment:webhook:edit']" type="warning" plain :disabled="endpointMultiple || mixedEndpointSelection" @click="batchEndpointStatus('1')">批量停用</el-button>
              <el-button v-hasPermi="['payment:webhook:edit']" type="danger" plain :disabled="endpointMultiple || mixedEndpointSelection" @click="deleteEndpoints()">删除</el-button>
            </div>
          </div>

          <el-table v-loading="endpointLoading" :data="endpointRows" border @selection-change="handleEndpointSelection">
            <el-table-column type="selection" width="52" align="center" />
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="名称" prop="endpointName" min-width="160" />
            <el-table-column label="回调地址" prop="endpointUrl" min-width="330" show-overflow-tooltip />
            <el-table-column label="订阅事件" min-width="230">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="type in row.eventTypes" :key="type" effect="plain">
                    {{ eventTypeLabel(type) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="平台" width="100" align="center">
              <template #default="{ row }">{{ platformLabel(row.platformFilter) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '0' ? 'success' : 'info'">
                  {{ row.status === '0' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="255" align="center">
              <template #default="{ row }">
                <el-button
                  v-hasPermi="['payment:webhook:edit']"
                  link
                  type="primary"
                  @click="openEditEndpoint(row as WebhookEndpointVO)"
                >
                  编辑
                </el-button>
                <el-button
                  v-hasPermi="['payment:webhook:edit']"
                  link
                  type="warning"
                  @click="rotateSecret(row as WebhookEndpointVO)"
                >
                  轮换密钥
                </el-button>
                <el-button
                  v-hasPermi="['payment:webhook:edit']"
                  link
                  type="success"
                  @click="testEndpoint(row as WebhookEndpointVO)"
                >
                  测试
                </el-button>
                <el-button
                  v-hasPermi="['payment:webhook:edit']"
                  link
                  type="danger"
                  @click="deleteEndpoints(row as WebhookEndpointVO)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="endpointTotal > 0"
            v-model:page="endpointQuery.pageNum"
            v-model:limit="endpointQuery.pageSize"
            :total="endpointTotal"
            @pagination="loadEndpoints"
          />
        </el-tab-pane>

        <el-tab-pane label="投递任务" name="outbox">
          <el-form ref="outboxQueryRef" :model="outboxQuery" :inline="true">
            <el-form-item label="Delivery ID" prop="deliveryId">
              <el-input
                v-model="outboxQuery.deliveryId"
                clearable
                placeholder="完整或精确 Delivery ID"
                style="width: 250px"
                @keyup.enter="searchOutbox"
              />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="outboxQuery.status" clearable placeholder="全部状态" style="width: 145px">
                <el-option label="待投递" value="PENDING" />
                <el-option label="投递中" value="DELIVERING" />
                <el-option label="等待重试" value="RETRYING" />
                <el-option label="已送达" value="DELIVERED" />
                <el-option label="已终止" value="DEAD" />
              </el-select>
            </el-form-item>
            <el-form-item label="端点" prop="endpointId">
              <el-select
                v-model="outboxQuery.endpointId"
                clearable
                filterable
                placeholder="全部端点"
                style="width: 180px"
              >
                <el-option
                  v-for="item in endpointOptions"
                  :key="item.id"
                  :label="item.endpointName"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="订单 ID" prop="aggregateId">
              <el-input v-model="outboxQuery.aggregateId" clearable placeholder="订单数据库 ID" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="searchOutbox">搜索</el-button>
              <el-button icon="Refresh" @click="resetOutboxQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="section-toolbar">
            <div>
              <h3>Webhook Outbox</h3>
              <p>同一业务事件的 eventId 始终稳定；接收方应按 eventId 幂等处理。</p>
            </div>
            <div class="refresh-control">
              <el-switch v-model="autoRefresh" active-text="5 秒自动刷新" />
              <el-button
                v-hasPermi="['payment:webhook:retry']"
                type="warning"
                plain
                :disabled="outboxMultiple || mixedOutboxSelection"
                @click="batchRetryOutbox"
              >
                批量重试
              </el-button>
              <el-button icon="Refresh" @click="loadOutbox">立即刷新</el-button>
            </div>
          </div>

          <el-table v-loading="outboxLoading" :data="outboxRows" border @selection-change="handleOutboxSelection">
            <el-table-column type="selection" width="52" :selectable="row => row.status !== 'DELIVERING'" />
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="Delivery ID" prop="deliveryId" min-width="245" show-overflow-tooltip />
            <el-table-column label="Event ID" prop="eventId" min-width="245" show-overflow-tooltip />
            <el-table-column label="端点" prop="endpointName" min-width="145" show-overflow-tooltip />
            <el-table-column label="事件" prop="eventType" min-width="175" />
            <el-table-column label="订单 ID" prop="aggregateId" min-width="165" show-overflow-tooltip />
            <el-table-column label="状态" width="105" align="center">
              <template #default="{ row }">
                <el-tag :type="outboxStatusType(row.status)">
                  {{ outboxStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="尝试" prop="attemptCount" width="75" align="center" />
            <el-table-column label="HTTP" prop="lastHttpStatus" width="80" align="center" />
            <el-table-column label="下次投递" width="190">
              <template #default="{ row }">{{ formatApiTime(row.nextAttemptAt) }}</template>
            </el-table-column>
            <el-table-column label="最后错误" prop="lastError" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" fixed="right" width="190" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="showOutbox(row.id)">详情</el-button>
                <el-button
                  v-if="row.status !== 'DELIVERING'"
                  v-hasPermi="['payment:webhook:retry']"
                  link
                  type="warning"
                  @click="retryOutbox(row as WebhookOutboxVO)"
                >
                  重试
                </el-button>
                <el-button
                  v-hasPermi="['payment:webhook:retry']"
                  link
                  type="success"
                  @click="replayOutbox(row as WebhookOutboxVO)"
                >
                  重放
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="outboxTotal > 0"
            v-model:page="outboxQuery.pageNum"
            v-model:limit="outboxQuery.pageSize"
            :total="outboxTotal"
            @pagination="loadOutbox"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="endpointEditor.visible"
      :title="endpointEditor.id ? '编辑 Webhook 端点' : '新增 Webhook 端点'"
      width="660px"
    >
      <el-form :model="endpointEditor.form" label-width="105px">
        <el-form-item v-if="showMerchantColumn" label="目标商户" required>
          <payment-merchant-target-select
            v-model="endpointEditor.form.merchantId"
            :disabled="Boolean(endpointEditor.id)"
            active-only
          />
        </el-form-item>
        <el-form-item label="端点名称" required>
          <el-input v-model="endpointEditor.form.endpointName" maxlength="100" />
        </el-form-item>
        <el-form-item label="回调地址" required>
          <el-input
            v-model="endpointEditor.form.endpointUrl"
            maxlength="1000"
            placeholder="https://example.com/payment/webhook"
          />
          <div class="form-tip">本地 Docker 可使用 http://host.docker.internal:19090/webhook。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="endpointEnabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="订阅事件" required>
          <el-checkbox-group v-model="endpointEditor.form.eventTypes">
            <el-checkbox v-for="item in eventTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="平台过滤">
          <el-radio-group v-model="endpointEditor.form.platformFilter">
            <el-radio-button value="ALL">全部</el-radio-button>
            <el-radio-button value="WECHAT">微信</el-radio-button>
            <el-radio-button value="ALIPAY">支付宝</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="endpointEditor.visible = false">取消</el-button>
        <el-button type="primary" :loading="endpointEditor.submitting" @click="saveEndpoint">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretDialog.visible" title="Webhook 密钥（仅显示一次）" width="680px">
      <el-alert
        title="请立即复制并安全保存。关闭窗口后管理端无法再次读取该明文密钥。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="1" border class="mt-3">
        <el-descriptions-item label="端点">{{ secretDialog.endpointName }}</el-descriptions-item>
        <el-descriptions-item label="回调地址">{{ secretDialog.endpointUrl }}</el-descriptions-item>
      </el-descriptions>
      <el-input
        v-model="secretDialog.secret"
        class="mt-3 secret-input"
        type="textarea"
        :rows="3"
        readonly
      />
      <template #footer>
        <el-button type="primary" icon="CopyDocument" @click="copySecret">复制密钥</el-button>
        <el-button @click="secretDialog.visible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="outboxDetailVisible" title="Webhook 投递详情" size="820px">
      <template v-if="outboxDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Delivery ID" :span="2">
            {{ outboxDetail.deliveryId }}
          </el-descriptions-item>
          <el-descriptions-item label="Event ID" :span="2">
            {{ outboxDetail.eventId }}
          </el-descriptions-item>
          <el-descriptions-item label="端点">{{ outboxDetail.endpointName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ outboxStatusLabel(outboxDetail.status) }}
          </el-descriptions-item>
          <el-descriptions-item label="回调地址" :span="2">
            {{ outboxDetail.endpointUrl }}
          </el-descriptions-item>
          <el-descriptions-item label="事件">{{ outboxDetail.eventType }}</el-descriptions-item>
          <el-descriptions-item label="订单 ID">{{ outboxDetail.aggregateId }}</el-descriptions-item>
          <el-descriptions-item label="尝试次数">{{ outboxDetail.attemptCount }}</el-descriptions-item>
          <el-descriptions-item label="最后 HTTP">{{ outboxDetail.lastHttpStatus ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatApiTime(outboxDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="送达时间">{{ formatApiTime(outboxDetail.deliveredAt) }}</el-descriptions-item>
          <el-descriptions-item label="最后错误" :span="2">
            {{ outboxDetail.lastError || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="outboxDetail.replayOfDeliveryId" label="重放来源" :span="2">
            {{ outboxDetail.replayOfDeliveryId }}
          </el-descriptions-item>
          <el-descriptions-item v-if="outboxDetail.replayReason" label="重放原因" :span="2">
            {{ outboxDetail.replayReason }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="mt-4">投递尝试日志</h4>
        <el-table :data="outboxDetail.deliveryLogs || []" border>
          <el-table-column label="次数" prop="attemptNumber" width="70" align="center" />
          <el-table-column label="请求时间" width="190">
            <template #default="{ row }">{{ formatApiTime(row.requestAt) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="95" align="right">
            <template #default="{ row }">{{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}</template>
          </el-table-column>
          <el-table-column label="HTTP" prop="httpStatus" width="80" align="center" />
          <el-table-column label="结果" width="85" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="响应/错误" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || row.responseExcerpt || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="PaymentWebhook" lang="ts">
import {
  batchRetryWebhookOutbox,
  batchUpdateWebhookEndpointStatus,
  createWebhookEndpoint,
  deleteWebhookEndpoints,
  getWebhookOutbox,
  listWebhookEndpoints,
  listWebhookOutbox,
  retryWebhookOutbox,
  replayWebhookOutbox,
  rotateWebhookSecret,
  testWebhookEndpoint,
  updateWebhookEndpoint
} from '@/api/payment';
import { formatApiTime } from '@/api/payment/time';
import type {
  WebhookEndpointForm,
  WebhookEndpointQuery,
  WebhookEndpointVO,
  WebhookOutboxQuery,
  WebhookOutboxVO
} from '@/api/payment/types';
import PaymentMerchantTargetSelect from '@/components/PaymentMerchantTargetSelect/index.vue';
import {
  hasMixedMerchantSelection,
  usePaymentMerchantScope
} from '@/hooks/payment/useMerchantScope';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import { useTableSelection } from '@/hooks/table/useTableSelection';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const activeTab = ref('endpoints');

const endpointLoading = ref(false);
const endpointRows = ref<WebhookEndpointVO[]>([]);
const {
  ids: endpointIds,
  selectedRows: selectedEndpointRows,
  multiple: endpointMultiple,
  handleSelectionChange: handleEndpointSelection,
  clearSelection: clearEndpointSelection
} = useTableSelection<WebhookEndpointVO>(item => item.id);
const endpointOptions = ref<WebhookEndpointVO[]>([]);
const endpointTotal = ref(0);
const endpointQueryRef = ref();
const endpointQuery = ref<WebhookEndpointQuery>({ pageNum: 1, pageSize: 10 });

const outboxLoading = ref(false);
const outboxRows = ref<WebhookOutboxVO[]>([]);
const {
  ids: outboxIds,
  selectedRows: selectedOutboxRows,
  multiple: outboxMultiple,
  handleSelectionChange: handleOutboxSelection,
  clearSelection: clearOutboxSelection
} = useTableSelection<WebhookOutboxVO>(item => item.id);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();
const mixedEndpointSelection = computed(() =>
  hasMixedMerchantSelection(selectedEndpointRows.value)
);
const mixedOutboxSelection = computed(() =>
  hasMixedMerchantSelection(selectedOutboxRows.value)
);
const outboxDetail = ref<WebhookOutboxVO>();
const outboxDetailVisible = ref(false);
const outboxTotal = ref(0);
const outboxQueryRef = ref();
const outboxQuery = ref<WebhookOutboxQuery>({ pageNum: 1, pageSize: 10 });
const autoRefresh = ref(true);
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const emptyEndpointForm = (): WebhookEndpointForm => ({
  endpointName: '',
  endpointUrl: '',
  status: '0',
  eventTypes: ['payment.order.paid'],
  platformFilter: 'ALL'
});
const eventTypeOptions = [
  { value: 'payment.transaction.observed', label: '观察到收入通知' },
  { value: 'payment.order.paid', label: '支付成功（通知确认）' },
  { value: 'payment.order.confirmed', label: '订单人工已确认' },
  { value: 'payment.order.reconciled', label: '订单内部已对账' },
  { value: 'payment.order.confirmation_revoked', label: '支付确认已撤销' },
  { value: 'payment.order.expired', label: '订单过期' },
  { value: 'payment.order.cancelled', label: '订单取消' },
  { value: 'payment.order.conflict', label: '匹配冲突' }
];
const endpointEditor = reactive({
  visible: false,
  submitting: false,
  id: undefined as string | number | undefined,
  form: emptyEndpointForm()
});
const endpointEnabled = computed({
  get: () => endpointEditor.form.status === '0',
  set: value => (endpointEditor.form.status = value ? '0' : '1')
});
const secretDialog = reactive({
  visible: false,
  endpointName: '',
  endpointUrl: '',
  secret: ''
});

const loadEndpoints = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  endpointLoading.value = true;
  try {
    const response = await listWebhookEndpoints(endpointQuery.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    endpointRows.value = response.data?.rows || [];
    endpointTotal.value = response.data?.total || 0;
    endpointOptions.value = endpointRows.value;
  } finally {
    endpointLoading.value = false;
  }
};
const loadEndpointOptions = async () => {
  const response = await listWebhookEndpoints({ pageNum: 1, pageSize: 100 });
  endpointOptions.value = response.data?.rows || [];
};
const searchEndpoints = () => {
  endpointQuery.value.pageNum = 1;
  loadEndpoints();
};
const resetEndpointQuery = () => {
  endpointQueryRef.value?.resetFields();
  endpointQuery.value = { pageNum: 1, pageSize: endpointQuery.value.pageSize };
  loadEndpoints();
};
const openCreateEndpoint = () => {
  endpointEditor.id = undefined;
  endpointEditor.form = emptyEndpointForm();
  endpointEditor.form.merchantId = defaultTargetMerchantId();
  endpointEditor.visible = true;
};
const openEditEndpoint = (row: WebhookEndpointVO) => {
  endpointEditor.id = row.id;
  endpointEditor.form = {
    merchantId: row.merchantId,
    endpointName: row.endpointName,
    endpointUrl: row.endpointUrl,
    status: row.status,
    eventTypes: [...(row.eventTypes || ['payment.order.paid'])],
    platformFilter: row.platformFilter || 'ALL'
  };
  endpointEditor.visible = true;
};
const showSecret = (endpoint: WebhookEndpointVO, secret: string) => {
  secretDialog.endpointName = endpoint.endpointName;
  secretDialog.endpointUrl = endpoint.endpointUrl;
  secretDialog.secret = secret;
  secretDialog.visible = true;
};
const saveEndpoint = async () => {
  if (!endpointEditor.id && !endpointEditor.form.merchantId) {
    proxy?.$modal.msgWarning('请选择目标商户');
    return;
  }
  if (!endpointEditor.form.endpointName.trim() || !endpointEditor.form.endpointUrl.trim()) {
    proxy?.$modal.msgWarning('端点名称和回调地址不能为空');
    return;
  }
  endpointEditor.submitting = true;
  try {
    if (endpointEditor.id) {
      await updateWebhookEndpoint(endpointEditor.id, endpointEditor.form);
      proxy?.$modal.msgSuccess('端点已更新');
    } else {
      const result = (await createWebhookEndpoint(endpointEditor.form)).data;
      showSecret(result.endpoint, result.webhookSecret);
    }
    endpointEditor.visible = false;
    await loadEndpoints();
  } finally {
    endpointEditor.submitting = false;
  }
};
const rotateSecret = async (row: WebhookEndpointVO) => {
  await proxy?.$modal.confirm(
    `轮换商户“${row.merchantName || row.merchantCode || row.merchantId}”的 Webhook“${row.endpointName}”密钥后，旧密钥立即失效，确认继续吗？`
  );
  const token = await requestPaymentStepUp('WEBHOOK_SECRET_WRITE', '轮换 Webhook 密钥');
  const result = (await rotateWebhookSecret(row.id, token)).data;
  showSecret(result.endpoint, result.webhookSecret);
};
const testEndpoint = async (row: WebhookEndpointVO) => {
  await testWebhookEndpoint(row.id);
  proxy?.$modal.msgSuccess('测试回调已进入投递队列');
  activeTab.value = 'outbox';
  await loadOutbox();
};
const batchEndpointStatus = async (status: '0' | '1') => {
  if (mixedEndpointSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  const action = status === '0' ? '启用' : '停用';
  await proxy?.$modal.confirm(`确认${action}选中的 ${endpointIds.value.length} 个 Webhook 端点吗？`);
  await batchUpdateWebhookEndpointStatus(endpointIds.value, status);
  proxy?.$modal.msgSuccess(`批量${action}成功`);
  await loadEndpoints();
};
const deleteEndpoints = async (row?: WebhookEndpointVO) => {
  if (!row && mixedEndpointSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  const selected = row ? [row.id] : endpointIds.value;
  const targetText = row
    ? `商户“${row.merchantName || row.merchantCode || row.merchantId}”的 Webhook 端点“${row.endpointName}”`
    : `选中的 ${selected.length} 个 Webhook 端点`;
  await proxy?.$modal.confirm(
    `确认删除${targetText}吗？已产生投递记录的端点只能停用。`
  );
  await deleteWebhookEndpoints(selected);
  proxy?.$modal.msgSuccess('删除成功');
  await loadEndpoints();
};
const copySecret = async () => {
  if (window.isSecureContext && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(secretDialog.secret);
  } else {
    const textarea = document.createElement('textarea');
    textarea.value = secretDialog.secret;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand('copy');
    textarea.remove();
    if (!copied) throw new Error('浏览器未允许复制');
  }
  proxy?.$modal.msgSuccess('密钥已复制');
};

const loadOutbox = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  outboxLoading.value = true;
  try {
    const response = await listWebhookOutbox(outboxQuery.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    outboxRows.value = response.data?.rows || [];
    outboxTotal.value = response.data?.total || 0;
  } finally {
    outboxLoading.value = false;
  }
};
const searchOutbox = () => {
  outboxQuery.value.pageNum = 1;
  loadOutbox();
};
const resetOutboxQuery = () => {
  outboxQueryRef.value?.resetFields();
  outboxQuery.value = { pageNum: 1, pageSize: outboxQuery.value.pageSize };
  loadOutbox();
};
const showOutbox = async (id: string | number) => {
  outboxDetail.value = (await getWebhookOutbox(id)).data;
  outboxDetailVisible.value = true;
};
const retryOutbox = async (row: WebhookOutboxVO) => {
  await retryWebhookOutbox(row.id);
  proxy?.$modal.msgSuccess('投递任务已重新进入队列');
  await loadOutbox();
  if (outboxDetailVisible.value && outboxDetail.value?.id === row.id) {
    await showOutbox(row.id);
  }
};
const batchRetryOutbox = async () => {
  if (mixedOutboxSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  await proxy?.$modal.confirm(`确认重试选中的 ${outboxIds.value.length} 个投递任务吗？`);
  await batchRetryWebhookOutbox(outboxIds.value);
  proxy?.$modal.msgSuccess('批量重试任务已进入队列');
  await loadOutbox();
};
const replayOutbox = async (row: WebhookOutboxVO) => {
  const result = await ElMessageBox.prompt(
    `商户“${row.merchantName || row.merchantCode || row.merchantId}”：重放会生成新的 deliveryId，并保留原投递记录。`,
    '重放 Webhook',
    {
      confirmButtonText: '确认重放',
      cancelButtonText: '取消',
      inputPlaceholder: '重放原因（选填）',
      inputValue: '管理端人工重放'
    }
  );
  await replayWebhookOutbox(row.id, result.value);
  proxy?.$modal.msgSuccess('新的重放任务已进入队列');
  await loadOutbox();
};
const eventTypeLabel = (value: string) =>
  eventTypeOptions.find(item => item.value === value)?.label || value;
const platformLabel = (value: string) =>
  ({ ALL: '全部', WECHAT: '微信', ALIPAY: '支付宝' })[value] || value;
const outboxStatusLabel = (status: string) =>
  ({
    PENDING: '待投递',
    DELIVERING: '投递中',
    RETRYING: '等待重试',
    DELIVERED: '已送达',
    DEAD: '已终止'
  })[status] || status;
const outboxStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' =>
  status === 'DELIVERED'
    ? 'success'
    : status === 'DEAD'
      ? 'danger'
      : status === 'RETRYING'
        ? 'warning'
        : status === 'DELIVERING'
          ? 'primary'
          : 'info';
const handleTabChange = async (name: string | number) => {
  if (name === 'outbox') {
    await loadEndpointOptions();
    await loadOutbox();
  }
};

watch(autoRefresh, enabled => {
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = enabled
    ? setInterval(() => {
        if (activeTab.value === 'outbox' && !outboxLoading.value) loadOutbox();
      }, 5000)
    : undefined;
}, { immediate: true });

watchScope(async () => {
  endpointQuery.value.pageNum = 1;
  outboxQuery.value.pageNum = 1;
  endpointEditor.visible = false;
  secretDialog.visible = false;
  outboxDetailVisible.value = false;
  clearEndpointSelection();
  clearOutboxSelection();
  await (activeTab.value === 'outbox'
    ? Promise.all([loadEndpointOptions(), loadOutbox()])
    : loadEndpoints());
});
onMounted(loadEndpoints);
onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer);
});
</script>

<style scoped lang="scss">
.section-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 16px;

  h3 {
    margin: 0 0 5px;
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.refresh-control {
  display: flex;
  align-items: center;
  gap: 14px;
}
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.secret-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  word-break: break-all;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
</style>
