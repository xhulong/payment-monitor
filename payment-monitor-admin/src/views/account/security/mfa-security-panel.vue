<template>
  <div class="mfa-security-panel">
    <section class="security-overview">
      <div class="overview-icon" :class="{ enabled: mfaEnabled }">
        <el-icon><Lock /></el-icon>
      </div>
      <div class="overview-content">
        <div class="overview-title-row">
          <div>
            <h3>多因素认证（MFA）</h3>
            <p>使用身份验证器验证码保护登录和敏感业务操作。</p>
          </div>
          <el-tag :type="mfaEnabled ? 'success' : 'warning'" effect="light">
            {{ mfaEnabled ? '已启用' : '尚未启用' }}
          </el-tag>
        </div>
        <div class="security-benefits">
          <span><el-icon><CircleCheck /></el-icon>登录二次验证</span>
          <span><el-icon><CircleCheck /></el-icon>敏感操作确认</span>
          <span><el-icon><CircleCheck /></el-icon>恢复码应急登录</span>
        </div>
      </div>
    </section>

    <section v-if="!setupState.secret" class="setup-entry">
      <div>
        <h4>{{ mfaEnabled ? '更换身份验证器' : '绑定身份验证器' }}</h4>
        <p v-if="mfaEnabled">
          重新配置后，新验证器将在验证码确认成功时生效，旧配置在此之前继续有效。
        </p>
        <p v-else>
          支持常见身份验证器应用。配置过程中需要扫描二维码并输入当前验证码。
        </p>
      </div>
      <div class="setup-actions">
        <el-button type="primary" :loading="loading" @click="beginSetup">
          {{ mfaEnabled ? '重新配置 MFA' : '开始配置 MFA' }}
        </el-button>
        <el-button
          v-if="mfaEnabled"
          type="danger"
          plain
          :loading="disabling"
          @click="disableMfa"
        >
          关闭 MFA
        </el-button>
      </div>
    </section>

    <section v-else class="binding-card">
      <div class="binding-heading">
        <div>
          <h4>绑定身份验证器</h4>
          <p>请依次完成二维码扫描和验证码确认。</p>
        </div>
        <el-button link @click="cancelSetup">取消配置</el-button>
      </div>

      <div class="binding-grid">
        <div class="binding-step qr-step">
          <div class="step-heading">
            <span class="step-number">1</span>
            <div>
              <strong>扫描二维码</strong>
              <p>使用身份验证器应用添加账号。</p>
            </div>
          </div>
          <div class="qr-box">
            <canvas ref="qrCanvas" />
          </div>
        </div>

        <div class="binding-step verification-step">
          <div class="step-heading">
            <span class="step-number">2</span>
            <div>
              <strong>确认验证码</strong>
              <p>输入身份验证器中显示的 6 位数字。</p>
            </div>
          </div>

          <label class="field-label">手动设置密钥</label>
          <el-input :model-value="setupState.secret" readonly>
            <template #append>
              <el-button @click="copy(setupState.secret)">复制</el-button>
            </template>
          </el-input>

          <label class="field-label verification-label">身份验证器验证码</label>
          <el-input
            v-model="confirmCode"
            class="verification-code"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            placeholder="000000"
            @keyup.enter="confirmSetup"
          />
          <el-button
            class="confirm-button"
            type="primary"
            :loading="confirming"
            @click="confirmSetup"
          >
            确认并启用
          </el-button>
        </div>
      </div>
    </section>

    <section class="recovery-card">
      <div class="recovery-heading">
        <div>
          <h4>恢复码</h4>
          <p>身份验证器暂时不可用时，可使用一枚恢复码完成登录。</p>
        </div>
        <el-button
          :disabled="!mfaEnabled"
          :loading="regenerating"
          @click="regenerate"
        >
          重新生成
        </el-button>
      </div>

      <el-alert
        v-if="recoveryCodes.length"
        type="warning"
        :closable="false"
        show-icon
        title="恢复码仅显示一次，请立即离线保存；重新生成后旧恢复码会全部失效。"
      />
      <p v-else class="recovery-placeholder">
        {{ mfaEnabled ? '如需更换恢复码，请点击“重新生成”。' : '完成 MFA 配置后将生成一次性恢复码。' }}
      </p>

      <div v-if="recoveryCodes.length" class="recovery-list">
        <code v-for="code in recoveryCodes" :key="code">{{ code }}</code>
      </div>

    </section>
  </div>
</template>

<script setup lang="ts">
import { CircleCheck, Lock } from '@element-plus/icons-vue';
import QRCode from 'qrcode';
import {
  confirmTotp,
  disableTotp,
  getTotpStatus,
  regenerateTotpRecoveryCodes,
  setupTotp
} from '@/api/payment';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import { startMfaSetupFlow } from './mfa-setup';

const mfaEnabled = ref(false);
const loading = ref(false);
const confirming = ref(false);
const regenerating = ref(false);
const disabling = ref(false);
const confirmCode = ref('');
const recoveryCodes = ref<string[]>([]);
const qrCanvas = ref<HTMLCanvasElement>();
const setupState = reactive({ secret: '', otpauthUri: '' });

const load = async () => {
  mfaEnabled.value = Boolean((await getTotpStatus()).data);
};

const renderQr = async () => {
  await nextTick();
  if (qrCanvas.value && setupState.otpauthUri) {
    await QRCode.toCanvas(qrCanvas.value, setupState.otpauthUri, {
      width: 196,
      margin: 1,
      errorCorrectionLevel: 'M'
    });
  }
};

const beginSetup = async () => {
  loading.value = true;
  try {
    const data = (
      await startMfaSetupFlow(
        mfaEnabled.value,
        requestPaymentStepUp,
        setupTotp
      )
    ).data;
    setupState.secret = data?.secret || '';
    setupState.otpauthUri = data?.otpauthUri || '';
    confirmCode.value = '';
    await renderQr();
  } finally {
    loading.value = false;
  }
};

const cancelSetup = () => {
  setupState.secret = '';
  setupState.otpauthUri = '';
  confirmCode.value = '';
};

const confirmSetup = async () => {
  if (!/^\d{6}$/.test(confirmCode.value)) {
    ElMessage.warning('请输入 6 位身份验证器验证码');
    return;
  }
  confirming.value = true;
  try {
    recoveryCodes.value = (await confirmTotp(confirmCode.value)).data || [];
    cancelSetup();
    ElMessage.success('多因素认证（MFA）已启用，请立即保存恢复码');
    await load();
  } finally {
    confirming.value = false;
  }
};

const regenerate = async () => {
  regenerating.value = true;
  try {
    const token = await requestPaymentStepUp('MFA_RECOVERY_CODES', '重新生成恢复码');
    recoveryCodes.value = (await regenerateTotpRecoveryCodes(token)).data || [];
    ElMessage.success('恢复码已重新生成，旧恢复码已失效');
  } finally {
    regenerating.value = false;
  }
};

const disableMfa = async () => {
  await ElMessageBox.confirm(
    '关闭后，登录和敏感操作将不再要求 MFA 验证，但现有权限校验仍然有效。确认关闭吗？',
    '关闭多因素认证',
    {
      type: 'warning',
      confirmButtonText: '验证并关闭',
      cancelButtonText: '取消'
    }
  );
  disabling.value = true;
  try {
    const token = await requestPaymentStepUp('MFA_DISABLE', '关闭 MFA');
    await disableTotp(token);
    recoveryCodes.value = [];
    cancelSetup();
    await load();
    ElMessage.success('多因素认证（MFA）已关闭');
  } finally {
    disabling.value = false;
  }
};

const copy = async (value: string) => {
  await navigator.clipboard.writeText(value);
  ElMessage.success('已复制');
};

onMounted(load);
</script>

<style scoped lang="scss">
.mfa-security-panel {
  display: grid;
  gap: 16px;
}

.security-overview,
.setup-entry,
.binding-card,
.recovery-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--el-bg-color);
}

.security-overview {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background:
    radial-gradient(circle at 90% 10%, rgb(64 112 255 / 12%), transparent 36%),
    var(--el-bg-color);
}

.overview-icon {
  display: grid;
  width: 50px;
  height: 50px;
  flex: 0 0 50px;
  place-items: center;
  border-radius: 15px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  font-size: 24px;

  &.enabled {
    color: var(--el-color-success);
    background: var(--el-color-success-light-9);
  }
}

.overview-content {
  min-width: 0;
  flex: 1;
}

.overview-title-row,
.recovery-heading,
.binding-heading,
.setup-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

h3,
h4,
p {
  margin: 0;
}

.overview-title-row h3 {
  font-size: 18px;
}

.overview-title-row p,
.setup-entry p,
.binding-heading p,
.step-heading p,
.recovery-heading p,
.recovery-placeholder {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.security-benefits {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 12px;
  color: var(--el-text-color-regular);
  font-size: 12px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  .el-icon {
    color: var(--el-color-success);
  }
}

.setup-entry,
.binding-card,
.recovery-card {
  padding: 20px;
}

.binding-heading {
  margin-bottom: 18px;
}

.binding-grid {
  display: grid;
  grid-template-columns: minmax(230px, 0.72fr) minmax(360px, 1.28fr);
  gap: 16px;
}

.binding-step {
  min-width: 0;
  padding: 18px;
  border-radius: 14px;
  background: var(--el-fill-color-lighter);
}

.step-heading {
  display: flex;
  gap: 10px;
}

.step-number {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 9px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-weight: 700;
}

.qr-box {
  display: grid;
  width: 216px;
  height: 216px;
  margin: 16px auto 0;
  place-items: center;
  border: 10px solid #fff;
  border-radius: 14px;
  box-shadow: 0 8px 24px rgb(31 45 61 / 8%);
}

.field-label {
  display: block;
  margin: 18px 0 7px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.verification-label {
  margin-top: 16px;
}

.verification-code :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.28em;
  text-align: center;
}

.confirm-button {
  width: 100%;
  margin-top: 14px;
}

.recovery-heading {
  align-items: flex-start;
  margin-bottom: 14px;
}

.recovery-placeholder {
  padding: 13px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}

.recovery-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;

  code {
    padding: 10px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    text-align: center;
  }
}

.setup-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

@media (max-width: 900px) {
  .binding-grid {
    grid-template-columns: 1fr;
  }

  .recovery-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .security-overview,
  .overview-title-row,
  .setup-entry,
  .recovery-heading,
  .binding-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .security-overview {
    padding: 16px;
  }

  .setup-entry,
  .binding-card,
  .recovery-card {
    padding: 16px;
  }

  .setup-actions,
  .recovery-heading .el-button {
    width: 100%;
  }

  .setup-actions {
    flex-direction: column;
  }

  .setup-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
