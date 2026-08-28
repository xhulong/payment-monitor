<template>
  <div class="p-2 app-release-page">
    <el-card shadow="never">
      <div class="toolbar">
        <div>
          <h3>Android App 版本</h3>
          <p>
            服务端会自动解析 APK SHA-256、包名和签名证书。当前线上版本不可删除，历史版本可按需清理。
          </p>
        </div>
        <div class="toolbar-actions">
          <el-upload
            :show-file-list="false"
            accept=".apk"
            :before-upload="beforeUpload"
          >
            <el-button type="primary">上传 APK</el-button>
          </el-upload>
          <el-button
            type="danger"
            plain
            :disabled="multiple"
            @click="remove()"
          >
            删除选中版本
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="mt-3">
      <el-table
        v-loading="loading"
        :data="rows"
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          type="selection"
          width="52"
          :selectable="canDelete"
        />
        <el-table-column label="版本" min-width="150">
          <template #default="{ row }">
            <div class="version-cell">
              <strong>{{ row.versionName }}</strong>
              <el-tag
                v-if="isLatestPublished(row)"
                size="small"
                type="success"
              >
                当前线上
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="versionCode"
          label="versionCode"
          width="120"
        />
        <el-table-column label="更新策略" width="120">
          <template #default="{ row }">
            {{
              appReleaseUpdateModeLabel(
                (row as AppReleaseVO).updateMode
              )
            }}
          </template>
        </el-table-column>
        <el-table-column
          prop="minSupportedVersionCode"
          label="最低版本"
          width="105"
        />
        <el-table-column label="更新说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.releaseNotes || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="APK SHA-256" min-width="210" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="hash-value">{{ row.sha256 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="statusTagType((row as AppReleaseVO).status)">
              {{ appReleaseStatusLabel((row as AppReleaseVO).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.publishedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="205" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openMetadataEditor(row as AppReleaseVO)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              link
              type="success"
              @click="publish(row as AppReleaseVO)"
            >
              发布
            </el-button>
            <el-tooltip
              :disabled="canDelete(row)"
              content="当前线上最新版本不能删除"
              placement="top"
            >
              <span>
                <el-button
                  link
                  type="danger"
                  :disabled="!canDelete(row)"
                  @click="remove(row as AppReleaseVO)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="uploadEditor.visible"
      title="上传 App 版本"
      width="620px"
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-4"
        title="签名证书 SHA-256 和 APK SHA-256 将由服务端自动提取并校验，无需手动填写。"
      />
      <el-form
        ref="uploadFormRef"
        :model="uploadEditor.form"
        :rules="uploadRules"
        label-width="160px"
      >
        <el-form-item label="APK">
          <span>{{ uploadEditor.file?.name }}</span>
        </el-form-item>
        <el-form-item label="versionCode" prop="versionCode">
          <el-input-number
            v-model="uploadEditor.form.versionCode"
            :min="1"
            :max="2147483647"
          />
        </el-form-item>
        <el-form-item label="versionName" prop="versionName">
          <el-input
            v-model="uploadEditor.form.versionName"
            maxlength="64"
            placeholder="必须与 APK 内版本名称一致"
          />
        </el-form-item>
        <el-form-item
          label="最低支持 versionCode"
          prop="minSupportedVersionCode"
        >
          <el-input-number
            v-model="uploadEditor.form.minSupportedVersionCode"
            :min="1"
            :max="uploadEditor.form.versionCode || 1"
          />
        </el-form-item>
        <el-form-item label="更新策略" prop="updateMode">
          <el-select v-model="uploadEditor.form.updateMode">
            <el-option label="可选更新" value="OPTIONAL" />
            <el-option label="要求更新" value="REQUIRED" />
            <el-option label="安全阻断" value="SECURITY_BLOCK" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="uploadEditor.form.enforcementAt"
            type="datetime"
            placeholder="留空时由服务端设置宽限期"
          />
        </el-form-item>
        <el-form-item label="更新说明" prop="releaseNotes">
          <el-input
            v-model="uploadEditor.form.releaseNotes"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="每行填写一项更新内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadEditor.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploadEditor.loading"
          @click="upload"
        >
          上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="metadataEditor.visible"
      title="编辑版本信息"
      width="650px"
      destroy-on-close
    >
      <el-descriptions :column="2" border class="mb-4">
        <el-descriptions-item label="版本">
          {{ metadataEditor.row?.versionName }}
        </el-descriptions-item>
        <el-descriptions-item label="versionCode">
          {{ metadataEditor.row?.versionCode }}
        </el-descriptions-item>
        <el-descriptions-item label="包名">
          {{ metadataEditor.row?.verifiedPackageName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="校验状态">
          {{ metadataEditor.row?.verificationStatus || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="签名证书 SHA-256" :span="2">
          <span class="hash-value">
            {{ metadataEditor.row?.signingCertificateSha256 }}
          </span>
        </el-descriptions-item>
      </el-descriptions>
      <el-form
        ref="metadataFormRef"
        :model="metadataEditor.form"
        :rules="metadataRules"
        label-width="160px"
      >
        <el-form-item
          label="最低支持 versionCode"
          prop="minSupportedVersionCode"
        >
          <el-input-number
            v-model="metadataEditor.form.minSupportedVersionCode"
            :min="1"
            :max="metadataEditor.row?.versionCode || 1"
          />
        </el-form-item>
        <el-form-item label="更新策略" prop="updateMode">
          <el-select v-model="metadataEditor.form.updateMode">
            <el-option label="可选更新" value="OPTIONAL" />
            <el-option label="要求更新" value="REQUIRED" />
            <el-option label="安全阻断" value="SECURITY_BLOCK" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="metadataEditor.form.enforcementAt"
            type="datetime"
            placeholder="非可选更新留空时沿用原宽限期"
          />
        </el-form-item>
        <el-form-item label="更新说明" prop="releaseNotes">
          <el-input
            v-model="metadataEditor.form.releaseNotes"
            type="textarea"
            :rows="6"
            maxlength="4000"
            show-word-limit
            placeholder="每行填写一项更新内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metadataEditor.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="metadataEditor.loading"
          @click="saveMetadata"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  deleteAppReleases,
  listAppReleases,
  publishAppRelease,
  updateAppRelease,
  uploadAppRelease
} from '@/api/payment';
import type { AppReleaseVO } from '@/api/payment/types';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import {
  appReleaseStatusLabel,
  appReleaseUpdateModeLabel,
  canDeleteAppRelease,
  findLatestPublishedId,
  toAppReleaseUpdateRequest,
  type AppReleaseMetadataForm
} from './app-release-state';

interface AppReleaseUploadForm extends AppReleaseMetadataForm {
  versionCode: number;
  versionName: string;
}

const createMetadataForm = (): AppReleaseMetadataForm => ({
  minSupportedVersionCode: 1,
  enforcementAt: undefined,
  updateMode: 'OPTIONAL',
  releaseNotes: ''
});

const createUploadForm = (versionCode = 1): AppReleaseUploadForm => ({
  versionCode,
  versionName: '',
  ...createMetadataForm(),
  minSupportedVersionCode: versionCode
});

const loading = ref(false);
const rows = ref<AppReleaseVO[]>([]);
const uploadFormRef = ref<ElFormInstance>();
const metadataFormRef = ref<ElFormInstance>();
const {
  ids,
  multiple,
  handleSelectionChange,
  clearSelection
} = useTableSelection<AppReleaseVO>(item => item.id);

const uploadEditor = reactive({
  visible: false,
  loading: false,
  file: undefined as File | undefined,
  form: createUploadForm()
});

const metadataEditor = reactive({
  visible: false,
  loading: false,
  row: undefined as AppReleaseVO | undefined,
  form: createMetadataForm()
});

const latestPublishedId = computed(() => findLatestPublishedId(rows.value));

const validateMinimumVersion = (
  versionCode: () => number,
  value: number,
  callback: (error?: Error) => void
) => {
  if (!Number.isInteger(value) || value < 1) {
    callback(new Error('请输入有效的最低支持 versionCode'));
    return;
  }
  if (value > versionCode()) {
    callback(new Error('最低支持 versionCode 不能高于当前版本'));
    return;
  }
  callback();
};

const uploadRules: ElFormRules = {
  versionCode: [
    { required: true, message: '请输入 versionCode', trigger: 'change' }
  ],
  versionName: [
    { required: true, message: '请输入 versionName', trigger: 'blur' },
    {
      pattern: /^[0-9A-Za-z._-]{1,64}$/,
      message: '只能包含字母、数字、点、下划线和短横线',
      trigger: 'blur'
    }
  ],
  minSupportedVersionCode: [
    {
      validator: (
        _rule: unknown,
        value: number,
        callback: (error?: Error) => void
      ) =>
        validateMinimumVersion(
          () => uploadEditor.form.versionCode,
          value,
          callback
        ),
      trigger: 'change'
    }
  ],
  updateMode: [
    { required: true, message: '请选择更新策略', trigger: 'change' }
  ]
};

const metadataRules: ElFormRules = {
  minSupportedVersionCode: [
    {
      validator: (
        _rule: unknown,
        value: number,
        callback: (error?: Error) => void
      ) =>
        validateMinimumVersion(
          () => metadataEditor.row?.versionCode || 1,
          value,
          callback
        ),
      trigger: 'change'
    }
  ],
  updateMode: [
    { required: true, message: '请选择更新策略', trigger: 'change' }
  ]
};

const load = async () => {
  loading.value = true;
  try {
    rows.value =
      (await listAppReleases({ pageNum: 1, pageSize: 100 })).data?.rows || [];
    clearSelection();
  } finally {
    loading.value = false;
  }
};

const beforeUpload = (file: File) => {
  const nextVersionCode =
    Math.max(0, ...rows.value.map(item => item.versionCode)) + 1;
  uploadEditor.file = file;
  Object.assign(uploadEditor.form, createUploadForm(nextVersionCode));
  uploadEditor.visible = true;
  nextTick(() => uploadFormRef.value?.clearValidate());
  return false;
};

const upload = async () => {
  if (!uploadEditor.file) return;
  await uploadFormRef.value?.validate();
  const metadata = toAppReleaseUpdateRequest(uploadEditor.form);
  const data = new FormData();
  data.append('apk', uploadEditor.file);
  data.append('versionCode', String(uploadEditor.form.versionCode));
  data.append('versionName', uploadEditor.form.versionName.trim());
  data.append(
    'minSupportedVersionCode',
    String(metadata.minSupportedVersionCode)
  );
  data.append('updateMode', metadata.updateMode);
  if (metadata.enforcementAt) {
    data.append('enforcementAt', metadata.enforcementAt);
  }
  if (metadata.releaseNotes) {
    data.append('releaseNotes', metadata.releaseNotes);
  }
  uploadEditor.loading = true;
  try {
    const token = await requestPaymentStepUp(
      'APP_RELEASE_WRITE',
      '上传 App 版本'
    );
    await uploadAppRelease(data, token);
    uploadEditor.visible = false;
    ElMessage.success('APK 已上传并通过自动校验');
    await load();
  } finally {
    uploadEditor.loading = false;
  }
};

const openMetadataEditor = (row: AppReleaseVO) => {
  metadataEditor.row = row;
  Object.assign(metadataEditor.form, {
    minSupportedVersionCode: row.minSupportedVersionCode,
    enforcementAt: row.enforcementAt ? new Date(row.enforcementAt) : undefined,
    updateMode: row.updateMode,
    releaseNotes: row.releaseNotes || ''
  });
  metadataEditor.visible = true;
  nextTick(() => metadataFormRef.value?.clearValidate());
};

const saveMetadata = async () => {
  const row = metadataEditor.row;
  if (!row) return;
  await metadataFormRef.value?.validate();
  metadataEditor.loading = true;
  try {
    const token = await requestPaymentStepUp(
      'APP_RELEASE_WRITE',
      '修改 App 版本信息'
    );
    await updateAppRelease(
      row.id,
      toAppReleaseUpdateRequest(metadataEditor.form),
      token
    );
    metadataEditor.visible = false;
    ElMessage.success('版本信息已更新');
    await load();
  } finally {
    metadataEditor.loading = false;
  }
};

const publish = async (row: AppReleaseVO) => {
  await ElMessageBox.confirm(
    `确认发布 ${row.versionName}（${row.versionCode}）吗？发布后将成为线上可下载版本。`,
    '发布版本'
  );
  const token = await requestPaymentStepUp(
    'APP_RELEASE_WRITE',
    '发布 App 版本'
  );
  await publishAppRelease(row.id, token);
  ElMessage.success('版本已发布');
  await load();
};

const remove = async (row?: AppReleaseVO) => {
  const selected = row ? [row.id] : [...ids.value];
  if (!selected.length) return;
  await ElMessageBox.confirm(
    `确认删除选中的 ${selected.length} 个版本吗？对应 APK 文件也会永久删除，此操作不可恢复。`,
    '删除 App 版本',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp(
    'APP_RELEASE_WRITE',
    '删除 App 历史版本'
  );
  await deleteAppReleases(selected, token);
  ElMessage.success('版本及 APK 文件已删除');
  await load();
};

const canDelete = (row: unknown) =>
  canDeleteAppRelease(row as AppReleaseVO, latestPublishedId.value);

const isLatestPublished = (row: unknown) => {
  const release = row as AppReleaseVO;
  return (
    release.status === 'PUBLISHED' &&
    String(release.id) === String(latestPublishedId.value)
  );
};

const statusTagType = (
  status: AppReleaseVO['status']
): 'success' | 'info' | 'warning' => {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'REVOKED') return 'info';
  return 'warning';
};

const formatTime = (value?: string) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

onMounted(load);
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.toolbar h3 {
  margin: 0 0 6px;
}

.toolbar p {
  margin: 0;
  color: #7d899c;
}

.toolbar-actions,
.version-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hash-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  overflow-wrap: anywhere;
}

@media (max-width: 768px) {
  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
