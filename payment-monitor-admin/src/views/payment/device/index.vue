<template>
  <div class="p-2">
    <el-card shadow="hover">
      <el-form ref="queryFormRef" :model="queryParams" :inline="true">
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="queryParams.deviceName" clearable placeholder="请输入设备名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" value="0" />
            <el-option label="禁用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="在线状态" prop="online">
          <el-select v-model="queryParams.online" clearable placeholder="全部" style="width: 140px">
            <el-option label="在线" :value="true" />
            <el-option label="离线" :value="false" />
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
          <div class="toolbar-actions">
            <h3>监听设备</h3>
            <p>查看设备心跳、上传时间及手机端同步队列。</p>
          </div>
          <div>
            <el-button
              v-hasPermi="['payment:device:pair']"
              type="primary"
              icon="Connection"
              @click="handleCreatePairingCode"
            >
              生成配对码
            </el-button>
            <el-button
              v-hasPermi="['payment:device:assignment']"
              icon="SetUp"
              @click="openAssignments"
            >
              主备配置
            </el-button>
            <el-button icon="Refresh" @click="getList">刷新</el-button>
            <el-button
              v-hasPermi="['payment:device:edit']"
              type="success"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchStatus('0')"
            >
              批量启用
            </el-button>
            <el-button
              v-hasPermi="['payment:device:edit']"
              type="warning"
              plain
              :disabled="multiple || mixedMerchantSelection"
              @click="batchStatus('1')"
            >
              批量禁用
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="deviceList" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" align="center" />
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="设备名称" prop="deviceName" min-width="170" show-overflow-tooltip />
        <el-table-column label="在线" width="85" align="center">
          <template #default="{ row }">
            <el-tag :type="row.online ? 'success' : 'info'">{{ row.online ? '在线' : '离线' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="监听健康" min-width="235">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag size="small" :type="row.monitoringEnabled ? 'success' : 'info'">监听开关</el-tag>
              <el-tag size="small" :type="row.listenerConnected ? 'success' : 'danger'">Listener</el-tag>
              <el-tag size="small" :type="row.foregroundRunning ? 'success' : 'danger'">前台服务</el-tag>
              <el-tag size="small" :type="row.notificationAccessGranted ? 'success' : 'danger'">通知权限</el-tag>
              <el-tag size="small" :type="row.batteryOptimizationIgnored ? 'success' : 'warning'">后台保护</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="队列" min-width="190">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag size="small">待传 {{ row.pendingCount || 0 }}</el-tag>
              <el-tag size="small" type="warning">重试 {{ row.retryingCount || 0 }}</el-tag>
              <el-tag size="small" type="danger">拒绝 {{ row.rejectedCount || 0 }}</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column label="版本" min-width="150">
          <template #default="{ row }">{{ row.appVersion || '-' }} / P{{ row.parserVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="最后心跳" width="190">
          <template #default="{ row }">{{ formatApiTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column label="最后上传" width="190">
          <template #default="{ row }">{{ formatApiTime(row.lastUploadAt) }}</template>
        </el-table-column>
        <el-table-column label="最后同步" width="190">
          <template #default="{ row }">{{ formatApiTime(row.lastSyncAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'">
              {{ row.status === '0' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="230" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button
              v-hasPermi="['payment:device:edit']"
              link
              type="primary"
              @click="toggleDevice(row as PaymentDeviceVO)"
            >
              {{ row.status === '0' ? '禁用' : '启用' }}
            </el-button>
            <el-button
              v-hasPermi="['payment:device:edit']"
              link
              type="danger"
              @click="revokeCredential(row as PaymentDeviceVO)"
            >
              撤销密钥
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

    <el-dialog v-model="pairingTargetDialog.visible" title="选择配对设备所属商户" width="460px">
      <el-form label-width="90px">
        <el-form-item label="目标商户" required>
          <payment-merchant-target-select
            v-model="pairingTargetDialog.merchantId"
            active-only
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pairingTargetDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmPairingTarget">生成配对码</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pairingDialog.visible"
      title="设备配对"
      width="440px"
      destroy-on-close
      @close="stopPairingSession"
    >
      <el-result
        v-if="pairingDialog.status === 'PAIRED'"
        icon="success"
        title="设备配对成功"
        :sub-title="`${pairingDialog.deviceName || '新设备'} 已加入设备列表`"
      />
      <div v-else class="pairing-content">
        <canvas ref="pairingCanvasRef" class="pairing-qrcode"></canvas>
        <div class="pairing-code">{{ pairingDialog.code }}</div>
        <div class="pairing-server">{{ pairingDialog.serverUrl }}</div>
        <div class="pairing-expire">
          有效期至 {{ formatApiTime(pairingDialog.expiresAt) }}
          <span v-if="pairingDialog.remainingSeconds > 0">（剩余 {{ pairingDialog.remainingSeconds }} 秒）</span>
          <span v-else>（已过期）</span>
        </div>
        <el-alert
          v-if="pairingDialog.status === 'EXPIRED'"
          type="error"
          title="配对码已过期，请重新生成后再在手机端输入。"
          :closable="false"
          show-icon
        />
        <el-alert v-else type="warning" :closable="false" show-icon>
          配对码只能使用一次，设备密钥仅在 App 配对成功时返回。
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="pairingDialog.visible = false">关闭</el-button>
        <el-button
          v-if="pairingDialog.status === 'EXPIRED'"
          type="primary"
          @click="handleCreatePairingCode"
        >
          重新生成配对码
        </el-button>
        <template v-else-if="pairingDialog.status === 'PENDING'">
          <el-button
            type="primary"
            :disabled="pairingDialog.remainingSeconds <= 0"
            @click="copyPairingCode"
          >
            复制配对码
          </el-button>
          <el-button
            type="success"
            :disabled="pairingDialog.remainingSeconds <= 0"
            @click="copyPairingPayload"
          >
            复制配对信息
          </el-button>
        </template>
      </template>
    </el-dialog>

    <el-dialog v-model="assignmentDialog.visible" title="主备观察设备配置" width="860px">
      <el-form v-if="showMerchantColumn" label-width="90px" class="mb-3">
        <el-form-item label="目标商户" required>
          <payment-merchant-target-select
            v-model="assignmentDialog.merchantId"
            active-only
            @update:model-value="loadAssignmentsForMerchant"
          />
        </el-form-item>
      </el-form>
      <el-alert
        title="所有设备继续热备监听和上传；服务端只根据心跳健康计算当前有效观察设备。"
        type="info"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-table :data="assignmentDialog.rows" border>
        <el-table-column label="平台" width="130">
          <template #default="{ row }">
            <el-select v-model="row.platform">
              <el-option label="微信" value="WECHAT" />
              <el-option label="支付宝" value="ALIPAY" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="设备" min-width="230">
          <template #default="{ row }">
            <el-select v-model="row.deviceId" filterable style="width: 100%">
              <el-option v-for="device in assignmentDevices" :key="device.id" :label="device.deviceName" :value="device.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-select v-model="row.role">
              <el-option label="主设备" value="PRIMARY" />
              <el-option label="备用设备" value="BACKUP" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="130">
          <template #default="{ row }"><el-input-number v-model="row.priority" :min="1" :max="9999" /></template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
        </el-table-column>
        <el-table-column label="状态" width="135">
          <template #default="{ row }">
            <el-tag v-if="row.effectiveObserver" type="success">当前有效</el-tag>
            <el-tag v-else-if="row.healthy" type="info">健康热备</el-tag>
            <el-tag v-else-if="row.id" type="danger">不健康</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="assignmentDialog.rows.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt-3" icon="Plus" @click="addAssignment">新增配置</el-button>
      <template #footer>
        <el-button @click="assignmentDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="assignmentDialog.submitting" @click="saveAssignments">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="设备详情与心跳历史" size="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ detail.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="App / 解析器">{{ detail.appVersion || '-' }} / {{ detail.parserVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后 IP">{{ detail.lastIp || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后心跳">{{ formatApiTime(detail.lastSeenAt) }}</el-descriptions-item>
          <el-descriptions-item label="最后上传">{{ formatApiTime(detail.lastUploadAt) }}</el-descriptions-item>
          <el-descriptions-item label="最后同步">{{ formatApiTime(detail.lastSyncAt) }}</el-descriptions-item>
          <el-descriptions-item label="配对时间">{{ formatApiTime(detail.pairedAt) }}</el-descriptions-item>
          <el-descriptions-item label="监听开关">{{ detail.monitoringEnabled ? '开启' : '关闭' }}</el-descriptions-item>
          <el-descriptions-item label="Listener">{{ detail.listenerConnected ? '已连接' : '已断开' }}</el-descriptions-item>
          <el-descriptions-item label="前台服务">{{ detail.foregroundRunning ? '运行中' : '已停止' }}</el-descriptions-item>
          <el-descriptions-item label="通知使用权">{{ detail.notificationAccessGranted ? '已授权' : '未授权' }}</el-descriptions-item>
          <el-descriptions-item label="电池优化">{{ detail.batteryOptimizationIgnored ? '已忽略' : '仍有限制' }}</el-descriptions-item>
          <el-descriptions-item label="最近支付通知">{{ formatApiTime(detail.lastNotificationAt) }}</el-descriptions-item>
          <el-descriptions-item label="健康问题" :span="2">
            <el-tag :type="detail.lastHealthIssue ? 'danger' : 'success'">
              {{ healthIssueLabel(detail.lastHealthIssue) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.recentHeartbeats || []" border class="mt-4" max-height="520">
          <el-table-column label="心跳时间" width="190">
            <template #default="{ row }">{{ formatApiTime(row.heartbeatAt) }}</template>
          </el-table-column>
          <el-table-column label="App / 解析器" min-width="150">
            <template #default="{ row }">{{ row.appVersion || '-' }} / {{ row.parserVersion || '-' }}</template>
          </el-table-column>
          <el-table-column label="待传" prop="pendingCount" width="70" />
          <el-table-column label="重试" prop="retryingCount" width="70" />
          <el-table-column label="拒绝" prop="rejectedCount" width="70" />
          <el-table-column label="客户端最后同步" width="190">
            <template #default="{ row }">{{ formatApiTime(row.lastSyncAt) }}</template>
          </el-table-column>
          <el-table-column label="健康" min-width="170">
            <template #default="{ row }">
              <el-tag :type="row.healthIssue ? 'danger' : 'success'">
                {{ healthIssueLabel(row.healthIssue) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="IP" prop="clientIp" min-width="130" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="PaymentDevice" lang="ts">
import QRCode from 'qrcode';
import {
  batchUpdatePaymentDeviceStatus,
  createPairingCode,
  getPairingStatus,
  getPaymentDevice,
  listDeviceAssignments,
  listPaymentDevices,
  saveDeviceAssignments,
  updatePaymentDeviceStatus
} from '@/api/payment';
import {
  createPairingStatusPoller,
  serializePairingQrPayload
} from '@/api/payment/pairing';
import type {
  DeviceAssignmentVO,
  PairingStatusVO,
  PaymentDeviceQuery,
  PaymentDeviceVO
} from '@/api/payment/types';
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
const deviceList = ref<PaymentDeviceVO[]>([]);
const { ids, selectedRows, multiple, handleSelectionChange, clearSelection } =
  useTableSelection<PaymentDeviceVO>(item => item.id);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();
const mixedMerchantSelection = computed(() =>
  hasMixedMerchantSelection(selectedRows.value)
);
const detail = ref<PaymentDeviceVO>();
const detailVisible = ref(false);
const queryFormRef = ref();
const pairingCanvasRef = ref<HTMLCanvasElement>();
const queryParams = ref<PaymentDeviceQuery>({
  pageNum: 1,
  pageSize: 10,
  deviceName: undefined,
  status: undefined,
  online: undefined
});
const pairingDialog = reactive({
  visible: false,
  pairingSessionId: '' as string | number,
  status: 'PENDING' as PairingStatusVO['status'],
  code: '',
  expiresAt: '',
  serverUrl: '',
  qrSchema: 1,
  remainingSeconds: 0,
  payload: '',
  deviceName: '',
  pairedAt: ''
});
const pairingTargetDialog = reactive({
  visible: false,
  merchantId: undefined as string | undefined
});
let countdownTimer: number | undefined;
let autoCloseTimer: number | undefined;
type AssignmentEditorRow = Partial<DeviceAssignmentVO> & {
  deviceId: string | number;
  platform: 'WECHAT' | 'ALIPAY';
  role: 'PRIMARY' | 'BACKUP';
  priority: number;
  enabled: boolean;
};
const assignmentDialog = reactive<{
  visible: boolean;
  submitting: boolean;
  merchantId?: string;
  rows: AssignmentEditorRow[];
}>({
  visible: false,
  submitting: false,
  merchantId: undefined,
  rows: []
});
const assignmentDevices = computed(() =>
  assignmentDialog.merchantId
    ? deviceList.value.filter(
        device => String(device.merchantId) === assignmentDialog.merchantId
      )
    : []
);

const getList = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listPaymentDevices(queryParams.value);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    deviceList.value = response.data?.rows || [];
    total.value = response.data?.total || 0;
  } finally {
    loading.value = false;
  }
};

const clearCountdownTimer = () => {
  if (countdownTimer !== undefined) {
    window.clearInterval(countdownTimer);
    countdownTimer = undefined;
  }
};

const clearAutoCloseTimer = () => {
  if (autoCloseTimer !== undefined) {
    window.clearTimeout(autoCloseTimer);
    autoCloseTimer = undefined;
  }
};

const pairingStatusPoller = createPairingStatusPoller({
  intervalMs: 1500,
  loadStatus: async pairingSessionId => {
    const response = await getPairingStatus(pairingSessionId);
    if (!response.data) {
      throw new Error('设备配对状态响应为空');
    }
    return response.data;
  },
  onStatus: async status => {
    pairingDialog.status = status.status;
    pairingDialog.expiresAt = status.expiresAt;
    if (status.status === 'PAIRED') {
      clearCountdownTimer();
      pairingDialog.deviceName = status.deviceName || '';
      pairingDialog.pairedAt = status.pairedAt || '';
      await getList();
      proxy?.$modal.msgSuccess(`设备“${status.deviceName || '新设备'}”配对成功`);
      clearAutoCloseTimer();
      autoCloseTimer = window.setTimeout(() => {
        pairingDialog.visible = false;
      }, 1200);
    } else if (status.status === 'EXPIRED') {
      pairingDialog.remainingSeconds = 0;
      clearCountdownTimer();
    }
  }
});

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryFormRef.value?.resetFields();
  queryParams.value.online = undefined;
  handleQuery();
};
const addAssignment = () => {
  assignmentDialog.rows.push({
    deviceId: assignmentDevices.value[0]?.id || '',
    platform: 'WECHAT',
    role: 'BACKUP',
    priority: 100,
    enabled: true
  });
};
const openAssignments = async () => {
  if (deviceList.value.length === 0) await getList();
  assignmentDialog.merchantId = defaultTargetMerchantId();
  assignmentDialog.rows = [];
  assignmentDialog.visible = true;
  if (!assignmentDialog.merchantId) return;
  await loadAssignmentsForMerchant(assignmentDialog.merchantId);
};
const loadAssignmentsForMerchant = async (merchantId?: string) => {
  assignmentDialog.rows = [];
  if (!merchantId) return;
  const response = await listDeviceAssignments(merchantId);
  assignmentDialog.rows = (response.data || []).map(item => ({ ...item }));
  if (assignmentDialog.rows.length === 0) addAssignment();
};
const saveAssignments = async () => {
  if (!assignmentDialog.merchantId) {
    proxy?.$modal.msgWarning('请选择目标商户');
    return;
  }
  assignmentDialog.submitting = true;
  try {
    const assignments = assignmentDialog.rows.map(item => ({
      deviceId: item.deviceId,
      platform: item.platform,
      role: item.role,
      priority: item.priority,
      enabled: item.enabled
    }));
    await saveDeviceAssignments({
      merchantId: assignmentDialog.merchantId,
      assignments
    });
    proxy?.$modal.msgSuccess('主备设备配置已保存');
    assignmentDialog.visible = false;
  } finally {
    assignmentDialog.submitting = false;
  }
};

const showDetail = async (id: string | number) => {
  detail.value = (await getPaymentDevice(id)).data;
  detailVisible.value = true;
};

const handleCreatePairingCode = async () => {
  const merchantId = defaultTargetMerchantId();
  if (!merchantId) {
    pairingTargetDialog.merchantId = undefined;
    pairingTargetDialog.visible = true;
    return;
  }
  await generatePairingCode(merchantId);
};
const confirmPairingTarget = async () => {
  if (!pairingTargetDialog.merchantId) {
    proxy?.$modal.msgWarning('请选择目标商户');
    return;
  }
  pairingTargetDialog.visible = false;
  await generatePairingCode(pairingTargetDialog.merchantId);
};
const generatePairingCode = async (merchantId: string) => {
  stopPairingSession();
  const response = await createPairingCode(merchantId);
  pairingDialog.pairingSessionId = response.data.pairingSessionId;
  pairingDialog.status = 'PENDING';
  pairingDialog.code = response.data.pairingCode;
  pairingDialog.expiresAt = response.data.expiresAt;
  pairingDialog.serverUrl = response.data.serverUrl;
  pairingDialog.qrSchema = response.data.qrSchema;
  pairingDialog.payload = serializePairingQrPayload(response.data);
  pairingDialog.deviceName = '';
  pairingDialog.pairedAt = '';
  updateCountdown();
  clearCountdownTimer();
  countdownTimer = window.setInterval(updateCountdown, 1000);
  pairingDialog.visible = true;
  pairingStatusPoller.start(pairingDialog.pairingSessionId);
  await nextTick();
  if (pairingCanvasRef.value) {
    await QRCode.toCanvas(pairingCanvasRef.value, pairingDialog.payload, {
      width: 220,
      margin: 1,
      errorCorrectionLevel: 'M'
    });
  }
};

const updateCountdown = () => {
  pairingDialog.remainingSeconds = Math.max(
    0,
    Math.ceil((new Date(pairingDialog.expiresAt).getTime() - Date.now()) / 1000)
  );
  if (
    pairingDialog.remainingSeconds === 0 &&
    pairingDialog.status === 'PENDING'
  ) {
    pairingDialog.status = 'EXPIRED';
    pairingStatusPoller.stop();
    clearCountdownTimer();
  }
};

const stopPairingSession = () => {
  pairingStatusPoller.stop();
  clearCountdownTimer();
  clearAutoCloseTimer();
};

const copyPairingCode = async () => {
  await navigator.clipboard.writeText(pairingDialog.code);
  proxy?.$modal.msgSuccess('配对码已复制');
};

const copyPairingPayload = async () => {
  await navigator.clipboard.writeText(pairingDialog.payload);
  proxy?.$modal.msgSuccess('配对信息已复制');
};

const toggleDevice = async (row: PaymentDeviceVO) => {
  const nextStatus = row.status === '0' ? '1' : '0';
  const action = nextStatus === '0' ? '启用' : '禁用';
  await proxy?.$modal.confirm(
    `确认${action}商户“${row.merchantName || row.merchantCode || row.merchantId}”的设备“${row.deviceName}”吗？`
  );
  await updatePaymentDeviceStatus(row.id, { status: nextStatus });
  proxy?.$modal.msgSuccess(`${action}成功`);
  getList();
};

const batchStatus = async (status: '0' | '1') => {
  if (mixedMerchantSelection.value) {
    proxy?.$modal.msgWarning('同一次批量操作只能处理一个商户');
    return;
  }
  const action = status === '0' ? '启用' : '禁用';
  await proxy?.$modal.confirm(`确认${action}选中的 ${ids.value.length} 台设备吗？`);
  await batchUpdatePaymentDeviceStatus(ids.value, status);
  proxy?.$modal.msgSuccess(`批量${action}成功`);
  getList();
};

const revokeCredential = async (row: PaymentDeviceVO) => {
  await proxy?.$modal.confirm(
    `撤销商户“${row.merchantName || row.merchantCode || row.merchantId}”的设备“${row.deviceName}”密钥后，设备需要重新配对，是否继续？`
  );
  await updatePaymentDeviceStatus(row.id, { status: '1', revokeCredential: true });
  proxy?.$modal.msgSuccess('设备密钥已撤销');
  getList();
};
const healthIssueLabel = (value?: string) =>
  ({
    MONITORING_DISABLED: '监听已关闭',
    NOTIFICATION_ACCESS_MISSING: '通知使用权缺失',
    FOREGROUND_SERVICE_STOPPED: '前台服务停止',
    LISTENER_DISCONNECTED: 'Listener 断开',
    BATTERY_OPTIMIZATION_ACTIVE: '电池优化限制中'
  })[value || ''] || (value ? value : '正常');

watchScope(async () => {
  queryParams.value.pageNum = 1;
  detailVisible.value = false;
  assignmentDialog.visible = false;
  pairingTargetDialog.visible = false;
  clearSelection();
  stopPairingSession();
  await getList();
});
onMounted(getList);
onUnmounted(() => {
  stopPairingSession();
});
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;

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

.pairing-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.pairing-qrcode {
  width: 220px;
  height: 220px;
}

.pairing-code {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 6px;
}

.pairing-server {
  max-width: 100%;
  color: var(--el-color-primary);
  overflow-wrap: anywhere;
}

.pairing-expire {
  color: var(--el-text-color-secondary);
}
</style>
