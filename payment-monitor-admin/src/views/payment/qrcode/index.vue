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
        <el-form-item label="名称" prop="assetName">
          <el-input v-model="queryParams.assetName" clearable placeholder="二维码名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 130px">
            <el-option label="启用" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
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
            <h3>收款二维码资产</h3>
            <p>保存微信或支付宝收款码内容；可使用 {amount}、{amountMinor}、{orderNo} 占位符。</p>
          </div>
          <div class="toolbar-actions">
            <el-button v-hasPermi="['payment:qrcode:add']" type="primary" icon="Plus" @click="openCreate">
              新增二维码
            </el-button>
            <el-button
              v-hasPermi="['payment:qrcode:edit']"
              type="success"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchStatus('0')"
            >
              批量启用
            </el-button>
            <el-button
              v-hasPermi="['payment:qrcode:edit']"
              type="warning"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchStatus('1')"
            >
              批量停用
            </el-button>
            <el-button
              v-hasPermi="['payment:qrcode:edit']"
              type="danger"
              plain
              icon="Delete"
              :disabled="multiple || mixedMerchantSelection"
              @click="handleDelete()"
            >
              删除
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" align="center" />
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="资产编码" prop="assetCode" min-width="170">
          <template #default="{ row }">
            <el-button link type="primary" @click="copyAssetCode(row.assetCode)">
              {{ row.assetCode }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="名称" prop="assetName" min-width="180" />
        <el-table-column label="平台" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.platform === 'WECHAT' ? 'success' : 'primary'">
              {{ row.platform === 'WECHAT' ? '微信' : '支付宝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="二维码内容" prop="qrContentTemplate" min-width="300" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'">
              {{ row.status === '0' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="更新时间" width="190">
          <template #default="{ row }">{{ formatApiTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row as QrAssetVO)">预览</el-button>
            <el-button
              v-hasPermi="['payment:qrcode:edit']"
              link
              type="primary"
              @click="openEdit(row as QrAssetVO)"
            >
              编辑
            </el-button>
            <el-button
              v-hasPermi="['payment:qrcode:edit']"
              link
              type="danger"
              @click="handleDelete(row as QrAssetVO)"
            >
              删除
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

    <el-dialog v-model="editor.visible" :title="editor.id ? '编辑收款二维码' : '新增收款二维码'" width="620px">
      <el-form :model="editor.form" label-width="110px">
        <el-form-item v-if="showMerchantColumn" label="目标商户" required>
          <payment-merchant-target-select
            v-model="editor.form.merchantId"
            :disabled="Boolean(editor.id)"
          />
        </el-form-item>
        <el-form-item label="资产编码">
          <el-input
            v-model="editor.form.assetCode"
            maxlength="64"
            placeholder="留空自动生成；订单 API 使用此编码"
          />
        </el-form-item>
        <el-form-item label="平台" required>
          <el-radio-group v-model="editor.form.platform">
            <el-radio-button value="WECHAT">微信</el-radio-button>
            <el-radio-button value="ALIPAY">支付宝</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="editor.form.assetName" maxlength="100" />
        </el-form-item>
        <el-form-item label="二维码内容" required>
          <el-input
            v-model="editor.form.qrContentTemplate"
            type="textarea"
            :rows="5"
            maxlength="4096"
            show-word-limit
            placeholder="粘贴扫码结果或支付链接"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editor.form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" :loading="editor.submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog.visible" title="二维码预览" width="390px">
      <div class="preview">
        <canvas ref="previewCanvas"></canvas>
        <div>{{ previewDialog.name }}</div>
        <small>预览使用示例金额 1.23 元和示例订单号。</small>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="PaymentQrAsset" lang="ts">
import QRCode from 'qrcode';
import {
  batchUpdateQrAssetStatus,
  createQrAsset,
  deleteQrAssets,
  listQrAssets,
  updateQrAsset
} from '@/api/payment';
import type { QrAssetForm, QrAssetQuery, QrAssetVO } from '@/api/payment/types';
import { formatApiTime } from '@/api/payment/time';
import PaymentMerchantTargetSelect from '@/components/PaymentMerchantTargetSelect/index.vue';
import {
  hasMixedMerchantSelection,
  usePaymentMerchantScope
} from '@/hooks/payment/useMerchantScope';
import { useTableSelection } from '@/hooks/table/useTableSelection';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const loading = ref(false);
const total = ref(0);
const rows = ref<QrAssetVO[]>([]);
const { ids, selectedRows, multiple, handleSelectionChange, clearSelection } =
  useTableSelection<QrAssetVO>(item => item.id);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();
const mixedMerchantSelection = computed(() =>
  hasMixedMerchantSelection(selectedRows.value)
);
const queryFormRef = ref();
const previewCanvas = ref<HTMLCanvasElement>();
const queryParams = ref<QrAssetQuery>({ pageNum: 1, pageSize: 10 });
const emptyForm = (): QrAssetForm => ({
  assetCode: '',
  platform: 'WECHAT',
  assetName: '',
  qrContentTemplate: '',
  status: '0',
  remark: ''
});
const editor = reactive({
  visible: false,
  submitting: false,
  id: undefined as string | number | undefined,
  form: emptyForm()
});
const previewDialog = reactive({ visible: false, name: '' });
const enabled = computed({
  get: () => editor.form.status === '0',
  set: value => (editor.form.status = value ? '0' : '1')
});

const getList = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listQrAssets(queryParams.value);
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
  handleQuery();
};
const openCreate = () => {
  editor.id = undefined;
  editor.form = emptyForm();
  editor.form.merchantId = defaultTargetMerchantId();
  editor.visible = true;
};
const openEdit = (row: QrAssetVO) => {
  editor.id = row.id;
  editor.form = {
    merchantId: row.merchantId,
    assetCode: row.assetCode,
    platform: row.platform,
    assetName: row.assetName,
    qrContentTemplate: row.qrContentTemplate,
    status: row.status,
    remark: row.remark
  };
  editor.visible = true;
};
const submit = async () => {
  if (showMerchantColumn.value && !editor.id && !editor.form.merchantId) {
    proxy?.$modal.msgWarning('请选择目标商户');
    return;
  }
  if (!editor.form.assetName.trim() || !editor.form.qrContentTemplate.trim()) {
    proxy?.$modal.msgWarning('名称和二维码内容不能为空');
    return;
  }
  editor.submitting = true;
  try {
    if (editor.id) await updateQrAsset(editor.id, editor.form);
    else await createQrAsset(editor.form);
    editor.visible = false;
    proxy?.$modal.msgSuccess('保存成功');
    getList();
  } finally {
    editor.submitting = false;
  }
};
const preview = async (row: QrAssetVO) => {
  previewDialog.name = row.assetName;
  previewDialog.visible = true;
  await nextTick();
  const content = row.qrContentTemplate
    .replaceAll('{amountMinor}', '123')
    .replaceAll('{amount}', '1.23')
    .replaceAll('{orderNo}', 'PM-DEMO');
  if (previewCanvas.value) {
    await QRCode.toCanvas(previewCanvas.value, content, { width: 280, margin: 1 });
  }
};

const copyAssetCode = async (value: string) => {
  await navigator.clipboard.writeText(value);
  proxy?.$modal.msgSuccess('资产编码已复制');
};

const batchStatus = async (status: '0' | '1') => {
  if (mixedMerchantSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  await proxy?.$modal.confirm(`确认${status === '0' ? '启用' : '停用'}选中的 ${ids.value.length} 个二维码吗？`);
  await batchUpdateQrAssetStatus(ids.value, status);
  proxy?.$modal.msgSuccess('批量状态更新成功');
  getList();
};

const handleDelete = async (row?: QrAssetVO) => {
  if (!row && mixedMerchantSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  const selected = row ? [row.id] : ids.value;
  const targetText = row
    ? `商户“${row.merchantName || row.merchantCode || row.merchantId}”的二维码“${row.assetName}”`
    : `选中的 ${selected.length} 个二维码`;
  await proxy?.$modal.confirm(
    `确认删除${targetText}吗？已产生订单的二维码将禁止删除。`
  );
  await deleteQrAssets(selected);
  proxy?.$modal.msgSuccess('删除成功');
  getList();
};

watchScope(async () => {
  queryParams.value.pageNum = 1;
  editor.visible = false;
  previewDialog.visible = false;
  clearSelection();
  await getList();
});
onMounted(getList);
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  h3 { margin: 0 0 5px; }
  p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
}
.toolbar-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }
.preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  small { color: var(--el-text-color-secondary); }
}
</style>
