<template>
  <div class="p-2">
    <el-card shadow="hover">
      <el-form :model="query" :inline="true">
        <el-form-item label="商户编码">
          <el-input v-model="query.merchantCode" clearable />
        </el-form-item>
        <el-form-item label="商户名称">
          <el-input v-model="query.name" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 120px">
            <el-option label="启用" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="load">查询</el-button>
          <el-button icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="hover" class="mt-3">
      <template #header>
        <div class="toolbar">
          <div>
            <h3>支付商户</h3>
            <p>平台管理员创建商户并绑定管理员；API 密钥只在创建或轮换时展示一次。</p>
          </div>
          <el-button
            v-if="merchantStore.context?.superAdmin"
            v-hasPermi="['payment:merchant:add']"
            type="primary"
            icon="Plus"
            @click="openCreate"
          >
            新增商户
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="编码" prop="merchantCode" min-width="140" />
        <el-table-column label="名称" prop="name" min-width="160" />
        <el-table-column label="时区" prop="timezone" min-width="150" />
        <el-table-column label="管理员" min-width="180">
          <template #default="{ row }">
            {{ row.adminUserName || '-' }}
            <small v-if="row.adminUserId">#{{ row.adminUserId }}</small>
          </template>
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
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <el-button
              v-if="merchantStore.context?.superAdmin"
              v-hasPermi="['payment:merchant:edit']"
              link
              type="primary"
              @click="openEdit(row as MerchantVO)"
            >
              编辑
            </el-button>
            <el-button
              v-hasPermi="['payment:merchant:key']"
              link
              type="warning"
              @click="openKeys(row as MerchantVO)"
            >
              API Key
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

    <el-dialog v-model="editor.visible" :title="editor.id ? '编辑商户' : '新增商户'" width="620px">
      <el-form :model="editor.form" label-width="120px">
        <el-form-item label="商户编码" required>
          <el-input v-model="editor.form.merchantCode" maxlength="64" placeholder="MERCHANT_A" />
        </el-form-item>
        <el-form-item label="商户名称" required>
          <el-input v-model="editor.form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="时区" required>
          <el-input v-model="editor.form.timezone" placeholder="Asia/Shanghai" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editor.form.status">
            <el-radio-button value="0">启用</el-radio-button>
            <el-radio-button value="1">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editor.form.remark" type="textarea" maxlength="500" />
        </el-form-item>
        <template v-if="!editor.id">
          <el-divider>首个商户管理员</el-divider>
          <el-form-item label="现有用户 ID">
            <el-input v-model="editor.form.adminUserId" placeholder="填写后不会创建新用户" />
          </el-form-item>
          <template v-if="!editor.form.adminUserId">
            <el-form-item label="登录账号">
              <el-input v-model="editor.form.adminUserName" maxlength="30" />
            </el-form-item>
            <el-form-item label="用户昵称">
              <el-input v-model="editor.form.adminNickName" maxlength="30" />
            </el-form-item>
            <el-form-item label="初始密码">
              <el-input v-model="editor.form.adminPassword" type="password" show-password />
            </el-form-item>
          </template>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" :loading="editor.submitting" @click="submitMerchant">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="keys.visible" :title="`${keys.merchant?.name || ''} API Key`" size="720px">
      <div class="toolbar mb-3">
        <el-button type="primary" icon="Plus" @click="createKey">创建 API Key</el-button>
        <el-button icon="Refresh" @click="loadKeys">刷新</el-button>
      </div>
      <el-table v-loading="keys.loading" :data="keys.rows" border>
        <el-table-column label="名称" prop="keyName" min-width="140" />
        <el-table-column label="Key ID" prop="keyId" min-width="270" show-overflow-tooltip />
        <el-table-column label="版本" prop="currentVersion" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ row.status === '0' ? '启用' : '已撤销' }}</template>
        </el-table-column>
        <el-table-column label="最后使用" width="190">
          <template #default="{ row }">{{ formatApiTime(row.lastUsedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="warning" @click="rotateKey(row as MerchantApiKeyVO)">轮换</el-button>
            <el-button
              v-if="row.status === '0'"
              link
              type="danger"
              @click="revokeKey(row as MerchantApiKeyVO)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="toolbar mt-4 mb-3">
        <div>
          <h3>最近 API 调用审计</h3>
          <p>记录请求接口、来源 IP、响应状态和耗时，不记录签名及请求密钥。</p>
        </div>
        <el-button icon="Refresh" @click="loadAudits">刷新</el-button>
      </div>
      <el-table v-loading="audits.loading" :data="audits.rows" border max-height="360">
        <el-table-column label="时间" width="190">
          <template #default="{ row }">{{ formatApiTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="Key ID" prop="keyId" min-width="170" show-overflow-tooltip />
        <el-table-column label="请求" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestMethod }} {{ row.requestPath }}</template>
        </el-table-column>
        <el-table-column label="IP" prop="clientIp" min-width="130" />
        <el-table-column label="结果" width="145">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'">
              {{ row.httpStatus }} / {{ row.resultCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.durationMs }} ms</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="secret.visible" title="请立即保存 API 凭据" width="620px" :close-on-click-modal="false">
      <el-alert type="warning" show-icon :closable="false">
        密钥仅展示这一次，关闭后不能再次查看。
      </el-alert>
      <el-descriptions :column="1" border class="mt-3">
        <el-descriptions-item label="Key ID">{{ secret.keyId }}</el-descriptions-item>
        <el-descriptions-item label="凭据版本">{{ secret.version }}</el-descriptions-item>
        <el-descriptions-item label="API Secret">
          <el-input :model-value="secret.value" readonly>
            <template #append>
              <el-button @click="copy(secret.value)">复制</el-button>
            </template>
          </el-input>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button type="primary" @click="secret.visible = false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="PaymentMerchant" lang="ts">
import {
  createMerchant,
  createMerchantApiKey,
  listMerchantApiKeys,
  listMerchantApiAudits,
  listMerchants,
  revokeMerchantApiKey,
  rotateMerchantApiKey,
  updateMerchant
} from '@/api/payment';
import type {
  MerchantApiAuditVO,
  MerchantApiKeyVO,
  MerchantForm,
  MerchantQuery,
  MerchantVO
} from '@/api/payment/types';
import { formatApiTime } from '@/api/payment/time';
import { useMerchantStore } from '@/store/modules/merchant';
import { requestPaymentStepUp } from '@/utils/payment-step-up';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const merchantStore = useMerchantStore();
const loading = ref(false);
const total = ref(0);
const rows = ref<MerchantVO[]>([]);
const query = ref<MerchantQuery>({ pageNum: 1, pageSize: 10 });
const emptyForm = (): MerchantForm => ({
  merchantCode: '',
  name: '',
  status: '0',
  timezone: 'Asia/Shanghai',
  remark: ''
});
const editor = reactive({
  visible: false,
  submitting: false,
  id: undefined as string | number | undefined,
  form: emptyForm()
});
const keys = reactive({
  visible: false,
  loading: false,
  merchant: undefined as MerchantVO | undefined,
  rows: [] as MerchantApiKeyVO[]
});
const secret = reactive({ visible: false, keyId: '', version: 0, value: '' });
const audits = reactive({
  loading: false,
  rows: [] as MerchantApiAuditVO[]
});

const load = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listMerchants(query.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    rows.value = response.data?.rows || [];
    total.value = response.data?.total || 0;
  } finally {
    loading.value = false;
  }
};
const reset = () => {
  query.value = { pageNum: 1, pageSize: 10 };
  load();
};
const openCreate = () => {
  editor.id = undefined;
  editor.form = emptyForm();
  editor.visible = true;
};
const openEdit = (row: MerchantVO) => {
  editor.id = row.id;
  editor.form = {
    merchantCode: row.merchantCode,
    name: row.name,
    status: row.status,
    timezone: row.timezone,
    remark: row.remark
  };
  editor.visible = true;
};
const submitMerchant = async () => {
  if (!editor.form.merchantCode || !editor.form.name) {
    proxy?.$modal.msgWarning('商户编码和名称不能为空');
    return;
  }
  editor.submitting = true;
  try {
    if (editor.id) await updateMerchant(editor.id, editor.form);
    else await createMerchant(editor.form);
    editor.visible = false;
    proxy?.$modal.msgSuccess('保存成功');
    await load();
    await merchantStore.load();
  } finally {
    editor.submitting = false;
  }
};
const openKeys = async (merchant: MerchantVO) => {
  keys.merchant = merchant;
  keys.visible = true;
  await Promise.all([loadKeys(), loadAudits()]);
};
const loadAudits = async () => {
  if (!keys.merchant) return;
  audits.loading = true;
  try {
    const response = await listMerchantApiAudits({
      merchantId: keys.merchant.id,
      pageNum: 1,
      pageSize: 20
    });
    audits.rows = response.data?.rows || [];
  } finally {
    audits.loading = false;
  }
};
const loadKeys = async () => {
  if (!keys.merchant) return;
  keys.loading = true;
  try {
    const response = await listMerchantApiKeys(keys.merchant.id);
    keys.rows = response.data || [];
  } finally {
    keys.loading = false;
  }
};
const showSecret = (data?: { apiKey: MerchantApiKeyVO; apiSecret: string }) => {
  if (!data) return;
  secret.keyId = data.apiKey.keyId;
  secret.version = data.apiKey.currentVersion;
  secret.value = data.apiSecret;
  secret.visible = true;
};
const createKey = async () => {
  if (!keys.merchant) return;
  const result = await ElMessageBox.prompt(
    `为商户“${keys.merchant.name}”创建 API Key，请输入名称`,
    '创建 API Key',
    {
      inputValue: '订单系统'
    }
  );
  const token = await requestPaymentStepUp('API_KEY_WRITE', '创建 API Key');
  const response = await createMerchantApiKey(keys.merchant.id, result.value, token);
  showSecret(response.data);
  await loadKeys();
};
const rotateKey = async (row: MerchantApiKeyVO) => {
  if (!keys.merchant) return;
  await ElMessageBox.confirm(
    `确认轮换商户“${keys.merchant.name}”的 API Key“${row.keyName}”吗？轮换后旧密钥立即失效。`,
    '确认轮换',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp('API_KEY_WRITE', '轮换 API Key');
  const response = await rotateMerchantApiKey(keys.merchant.id, row.id, token);
  showSecret(response.data);
  await loadKeys();
};
const revokeKey = async (row: MerchantApiKeyVO) => {
  if (!keys.merchant) return;
  await ElMessageBox.confirm(
    `确认撤销商户“${keys.merchant.name}”的 API Key“${row.keyName}”吗？撤销后不能继续访问商户订单 API。`,
    '确认撤销',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp('API_KEY_WRITE', '撤销 API Key');
  await revokeMerchantApiKey(keys.merchant.id, row.id, token);
  await loadKeys();
};
const copy = async (value: string) => {
  await navigator.clipboard.writeText(value);
  proxy?.$modal.msgSuccess('已复制');
};

onMounted(async () => {
  await merchantStore.load().catch(() => undefined);
  await load();
});
watch(
  () => merchantStore.scopeVersion,
  async () => {
    query.value.pageNum = 1;
    editor.visible = false;
    keys.visible = false;
    secret.visible = false;
    keys.merchant = undefined;
    await load();
  }
);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.toolbar h3 {
  margin: 0;
}
.toolbar p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}
</style>
