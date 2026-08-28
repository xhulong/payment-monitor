<template>
  <div class="app-container mail-settings-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>邮件设置</h2>
            <p>配置全平台统一 SMTP 发件服务，保存后无需重启即可生效。</p>
          </div>
          <el-tag :type="form.enabled ? 'success' : 'info'">
            {{ form.enabled ? '服务已启用' : '服务已停用' }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="settingsSource === 'ENVIRONMENT'"
        title="当前使用环境变量中的启动回退配置，保存后将切换为数据库动态配置。"
        type="info"
        show-icon
        :closable="false"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
        class="settings-form"
      >
        <el-divider content-position="left">服务状态</el-divider>
        <el-form-item label="启用邮件服务">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-divider content-position="left">SMTP 服务器</el-divider>
        <div class="form-grid">
          <el-form-item label="SMTP 主机" prop="host">
            <el-input v-model="form.host" placeholder="smtp.example.com" />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
          </el-form-item>
          <el-form-item label="连接模式" prop="securityMode">
            <el-select v-model="form.securityMode">
              <el-option label="SSL（常用端口 465）" value="SSL" />
              <el-option label="STARTTLS（常用端口 587）" value="STARTTLS" />
              <el-option label="不加密（仅受控网络）" value="NONE" />
            </el-select>
          </el-form-item>
          <el-form-item label="启用 SMTP 认证">
            <el-switch v-model="form.authEnabled" />
          </el-form-item>
          <el-form-item label="SMTP 用户名" prop="username">
            <el-input v-model="form.username" :disabled="!form.authEnabled" />
          </el-form-item>
          <el-form-item label="密码 / 授权码" prop="password">
            <el-input
              v-model="form.password"
              :disabled="!form.authEnabled"
              type="password"
              show-password
              :placeholder="passwordPlaceholder"
              autocomplete="new-password"
            />
            <div class="field-tip">留空表示保留已保存的密码，页面永不回显密码内容。</div>
          </el-form-item>
        </div>

        <el-form-item v-if="!form.authEnabled && passwordConfigured" label="清除已保存密码">
          <el-checkbox v-model="form.clearPassword">关闭认证并清除已保存的密码</el-checkbox>
        </el-form-item>

        <el-divider content-position="left">发件人</el-divider>
        <div class="form-grid">
          <el-form-item label="发件人名称" prop="fromName">
              <el-input v-model="form.fromName" placeholder="LuLuPay" />
          </el-form-item>
          <el-form-item label="发件邮箱" prop="fromAddress">
            <el-input v-model="form.fromAddress" placeholder="noreply@example.com" />
          </el-form-item>
        </div>

        <el-divider content-position="left">超时设置</el-divider>
        <div class="form-grid">
          <el-form-item label="连接超时（毫秒）" prop="connectionTimeoutMs">
            <el-input-number
              v-model="form.connectionTimeoutMs"
              :min="1000"
              :max="120000"
              :step="1000"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="读取超时（毫秒）" prop="readTimeoutMs">
            <el-input-number
              v-model="form.readTimeoutMs"
              :min="1000"
              :max="120000"
              :step="1000"
              controls-position="right"
            />
          </el-form-item>
        </div>

        <el-form-item>
          <el-button
            v-hasPermi="['system:mail-settings:edit']"
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存邮件设置
          </el-button>
          <el-button
            v-hasPermi="['system:mail-settings:test']"
            :disabled="!form.enabled"
            @click="openTestDialog"
          >
            发送测试邮件
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="testDialogVisible" title="发送测试邮件" width="460px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="收件邮箱">
          <el-input v-model="testRecipient" placeholder="请输入测试邮件收件地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="testing" @click="sendTest">发送测试邮件</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  getMailSettings,
  testMailSettings,
  updateMailSettings
} from '@/api/system/mail';
import type {
  MailSecurityMode,
  MailSettingsForm
} from '@/api/system/mail/types';
import { getUserProfile } from '@/api/system/user';
import { requestPaymentStepUp } from '@/utils/payment-step-up';

const formRef = ref<ElFormInstance>();
const saving = ref(false);
const testing = ref(false);
const testDialogVisible = ref(false);
const testRecipient = ref('');
const passwordConfigured = ref(false);
const settingsSource = ref<'DATABASE' | 'ENVIRONMENT'>('ENVIRONMENT');

const form = reactive<MailSettingsForm>({
  enabled: false,
  host: '',
  port: 465,
  authEnabled: true,
  username: '',
  password: '',
  clearPassword: false,
  fromName: 'LuLuPay',
  fromAddress: '',
  securityMode: 'SSL',
  connectionTimeoutMs: 10000,
  readTimeoutMs: 10000
});

const validatePortMode = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (form.securityMode === 'SSL' && form.port === 587) {
    callback(new Error('587 端口通常应使用 STARTTLS'));
    return;
  }
  if (form.securityMode === 'STARTTLS' && form.port === 465) {
    callback(new Error('465 端口通常应使用 SSL'));
    return;
  }
  callback();
};

const rules: ElFormRules = {
  host: [{ required: true, message: '请输入 SMTP 主机', trigger: 'blur' }],
  port: [
    { required: true, message: '请输入 SMTP 端口', trigger: 'change' },
    { validator: validatePortMode, trigger: 'change' }
  ],
  securityMode: [{ required: true, message: '请选择连接模式', trigger: 'change' }],
  username: [{
    validator: (_rule, value, callback) => {
      if (form.authEnabled && !String(value || '').trim()) {
        callback(new Error('启用认证时必须填写 SMTP 用户名'));
        return;
      }
      callback();
    },
    trigger: 'blur'
  }],
  password: [{
    validator: (_rule, value, callback) => {
      if (form.authEnabled && !passwordConfigured.value && !String(value || '').trim()) {
        callback(new Error('启用认证时必须填写密码或授权码'));
        return;
      }
      callback();
    },
    trigger: 'blur'
  }],
  fromName: [{ required: true, message: '请输入发件人名称', trigger: 'blur' }],
  fromAddress: [
    { required: true, message: '请输入发件邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
  ]
};

const passwordPlaceholder = computed(() =>
  passwordConfigured.value ? '已配置，留空表示不修改' : '请输入密码或授权码'
);

const load = async () => {
  const response = await getMailSettings();
  const settings = response.data;
  Object.assign(form, {
    enabled: settings.enabled,
    host: settings.host,
    port: settings.port,
    authEnabled: settings.authEnabled,
    username: settings.username || '',
    password: '',
    clearPassword: false,
    fromName: settings.fromName,
    fromAddress: settings.fromAddress,
    securityMode: settings.securityMode as MailSecurityMode,
    connectionTimeoutMs: settings.connectionTimeoutMs,
    readTimeoutMs: settings.readTimeoutMs
  });
  passwordConfigured.value = settings.passwordConfigured;
  settingsSource.value = settings.source;
};

const save = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || saving.value) return;
  saving.value = true;
  try {
    const token = await requestPaymentStepUp('MAIL_SETTINGS_CHANGE', '保存邮件设置');
    const response = await updateMailSettings(
      {
        ...form,
        host: form.host.trim(),
        username: form.username?.trim(),
        password: form.password || undefined,
        fromName: form.fromName.trim(),
        fromAddress: form.fromAddress.trim().toLowerCase()
      },
      token
    );
    passwordConfigured.value = response.data.passwordConfigured;
    settingsSource.value = response.data.source;
    form.password = '';
    form.clearPassword = false;
    ElMessage.success('邮件设置已保存并立即生效');
  } finally {
    saving.value = false;
  }
};

const openTestDialog = async () => {
  if (!testRecipient.value) {
    const profile = await getUserProfile();
    testRecipient.value = profile.data.user.email || '';
  }
  testDialogVisible.value = true;
};

const sendTest = async () => {
  const recipient = testRecipient.value.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(recipient)) {
    ElMessage.warning('请输入正确的测试邮件收件地址');
    return;
  }
  if (testing.value) return;
  testing.value = true;
  try {
    const token = await requestPaymentStepUp('MAIL_SETTINGS_TEST', '发送 SMTP 测试邮件');
    await testMailSettings(recipient, token);
    testDialogVisible.value = false;
    ElMessage.success('测试邮件已发送');
  } finally {
    testing.value = false;
  }
};

onMounted(load);
</script>

<style scoped lang="scss">
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-heading h2 {
  margin: 0;
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.settings-form {
  max-width: 1040px;
  margin-top: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-input-number) {
  width: 100%;
}

.field-tip {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
