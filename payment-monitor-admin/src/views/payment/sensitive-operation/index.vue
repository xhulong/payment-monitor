<template>
  <div class="p-2">
    <el-card shadow="hover">
      <template #header>
        <div class="toolbar">
          <div>
            <h3>敏感操作记录</h3>
            <p>记录通过 MFA 或登录会话确认执行的强制补单和撤销支付确认。</p>
          </div>
          <el-button icon="Refresh" @click="load">刷新</el-button>
        </div>
      </template>

      <el-form :model="query" :inline="true">
        <el-form-item label="操作类型">
          <el-select
            v-model="query.operationType"
            clearable
            style="width: 180px"
          >
            <el-option label="强制补单" value="FORCE_MATCH" />
            <el-option label="撤销确认" value="REVERSE_CONFIRMATION" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标 ID">
          <el-input
            v-model="targetIdInput"
            clearable
            placeholder="订单或交易 ID"
            style="width: 210px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="search">
            查询
          </el-button>
          <el-button icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column
          label="记录 ID"
          prop="id"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="操作类型" width="130">
          <template #default="{ row }">
            <el-tag type="warning" effect="plain">
              {{ operationLabel(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标" min-width="210">
          <template #default="{ row }">
            {{ targetLabel(row.targetType) }} / {{ row.targetId }}
          </template>
        </el-table-column>
        <el-table-column
          label="原因"
          prop="reason"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column label="操作人" prop="operatedBy" width="110" />
        <el-table-column label="验证方式" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.verificationMethod === 'MFA' ? 'success' : 'info'">
              {{ verificationMethodLabel(row.verificationMethod) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行时间" width="190">
          <template #default="{ row }">
            {{ formatApiTime(row.operatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="90" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="show(row as SensitiveOperationVO)"
            >
              详情
            </el-button>
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

    <el-drawer
      v-model="detailVisible"
      title="敏感操作详情"
      size="760px"
    >
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录 ID" :span="2">
            {{ detail.id }}
          </el-descriptions-item>
          <el-descriptions-item label="操作类型">
            {{ operationLabel(detail.operationType) }}
          </el-descriptions-item>
          <el-descriptions-item label="验证方式">
            {{ verificationMethodLabel(detail.verificationMethod) }}
          </el-descriptions-item>
          <el-descriptions-item label="目标类型">
            {{ targetLabel(detail.targetType) }}
          </el-descriptions-item>
          <el-descriptions-item label="目标 ID">
            {{ detail.targetId }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ detail.operatedBy }}
          </el-descriptions-item>
          <el-descriptions-item label="执行时间">
            {{ formatApiTime(detail.operatedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">
            {{ detail.reason || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <h4>请求参数</h4>
        <pre class="json-block">{{ pretty(detail.requestPayload) }}</pre>
        <h4>执行前快照</h4>
        <pre class="json-block">{{ pretty(detail.beforeSnapshot) }}</pre>
        <h4>执行后快照</h4>
        <pre class="json-block">{{ pretty(detail.afterSnapshot) }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="SensitiveOperation" lang="ts">
import {
  getSensitiveOperation,
  listSensitiveOperations
} from '@/api/payment';
import { formatApiTime } from '@/api/payment/time';
import type {
  SensitiveOperationQuery,
  SensitiveOperationVO
} from '@/api/payment/types';
import { usePaymentMerchantScope } from '@/hooks/payment/useMerchantScope';

const loading = ref(false);
const total = ref(0);
const rows = ref<SensitiveOperationVO[]>([]);
const query = ref<SensitiveOperationQuery>({
  pageNum: 1,
  pageSize: 10
});
const targetIdInput = ref('');
const detail = ref<SensitiveOperationVO>();
const detailVisible = ref(false);
const {
  merchantStore,
  showMerchantColumn,
  watchScope
} = usePaymentMerchantScope();

const load = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listSensitiveOperations(query.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    rows.value = response.data?.rows || [];
    total.value = response.data?.total || 0;
  } finally {
    loading.value = false;
  }
};

const search = () => {
  query.value.pageNum = 1;
  query.value.targetId = targetIdInput.value.trim() || undefined;
  load();
};

const reset = () => {
  targetIdInput.value = '';
  query.value = { pageNum: 1, pageSize: query.value.pageSize };
  load();
};

const show = async (row: SensitiveOperationVO) => {
  detail.value = (await getSensitiveOperation(row.id)).data;
  detailVisible.value = true;
};

const operationLabel = (value: string) =>
  ({
    FORCE_MATCH: '强制补单',
    REVERSE_CONFIRMATION: '撤销确认'
  })[value] || value;

const targetLabel = (value: string) =>
  ({
    PAYMENT_ORDER: '支付订单',
    PAYMENT_TRANSACTION: '支付交易'
  })[value] || value;

const verificationMethodLabel = (value: string) =>
  ({
    MFA: 'MFA',
    SESSION: '登录会话'
  })[value] || value;

const pretty = (value?: string) => {
  if (!value) return '-';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

watchScope(async () => {
  query.value.pageNum = 1;
  detailVisible.value = false;
  await load();
});
onMounted(load);
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  h3 {
    margin: 0 0 6px;
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }

.json-block {
  padding: 14px;
  overflow: auto;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
