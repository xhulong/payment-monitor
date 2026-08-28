<template>
  <div class="login-page">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>
    <div class="ambient ambient-three"></div>

    <main class="login-shell">
      <section class="brand-panel">
        <div class="brand-header">
          <img :src="luluPayLogo" class="brand-mark" alt="LuLuPay Logo" />
          <div>
            <span class="brand-kicker">码支付</span>
            <strong class="brand-name">LuLuPay</strong>
          </div>
        </div>

        <div class="brand-content">
          <span class="brand-overline">收款确认 · 设备协同 · 商户平台</span>
          <h1>让每一笔收款，<br /><em>及时被看见。</em></h1>
          <p>
            从通知监听到订单确认，统一管理商户、设备、支付事件和回调。
            让经营者更快知道收款结果，让开发者更稳接入支付流程。
          </p>
        </div>

        <div class="brand-flow">
          <div class="flow-line"></div>
          <div class="flow-item">
            <span class="flow-index">01</span>
            <strong>监听</strong>
            <small>实时捕获通知</small>
          </div>
          <div class="flow-item">
            <span class="flow-index">02</span>
            <strong>确认</strong>
            <small>订单状态可追踪</small>
          </div>
          <div class="flow-item">
            <span class="flow-index">03</span>
            <strong>回调</strong>
            <small>业务系统可接收</small>
          </div>
        </div>

        <div class="brand-footer">
          <span class="status-dot"></span>
          <span>数据按商户隔离</span>
        </div>
      </section>

      <section class="auth-panel">
        <div class="auth-header">
          <router-link to="/overview" class="auth-home-link">← 产品首页</router-link>
          <div>
            <span class="auth-eyebrow">WELCOME BACK</span>
            <h2>{{ mfaChallengeToken ? '完成二次验证' : '登录控制台' }}</h2>
            <p>
              {{
                mfaChallengeToken
                  ? '输入身份验证器中的 6 位验证码，或使用一枚未使用的恢复码。'
                  : '登录后管理您的商户和支付通知设备。'
              }}
            </p>
          </div>
          <lang-select />
        </div>

        <el-form
          v-if="!mfaChallengeToken"
          ref="loginRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
        >
          <el-form-item prop="username">
            <label class="field-label" for="login-username">账号</label>
            <el-input
              id="login-username"
              v-model="loginForm.username"
              type="text"
              size="large"
              auto-complete="username"
              placeholder="请输入用户名或邮箱"
            >
              <template #prefix>
                <svg-icon icon-class="user" class="input-icon" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <label class="field-label" for="login-password">密码</label>
            <el-input
              id="login-password"
              v-model="loginForm.password"
              type="password"
              size="large"
              auto-complete="current-password"
              placeholder="请输入登录密码"
              show-password
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <svg-icon icon-class="password" class="input-icon" />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item v-if="captchaEnabled" prop="code" class="captcha-form-item">
            <label class="field-label" for="login-code">验证码</label>
            <div class="captcha-row">
              <el-input
                id="login-code"
                v-model="loginForm.code"
                size="large"
                auto-complete="off"
                placeholder="请输入图形验证码"
                @keyup.enter="handleLogin"
              >
                <template #prefix>
                  <svg-icon icon-class="validCode" class="input-icon" />
                </template>
              </el-input>
              <button type="button" class="captcha-image" aria-label="刷新验证码" @click="getCode">
                <img v-if="codeUrl" :src="codeUrl" alt="图形验证码" />
                <span v-else>加载中</span>
              </button>
            </div>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="loginForm.rememberMe">记住登录状态</el-checkbox>
            <router-link class="forgot-link" to="/forgot-password">忘记密码</router-link>
          </div>

          <el-button
            :loading="loading"
            :disabled="loading"
            size="large"
            type="primary"
            native-type="button"
            class="submit-button"
            @click.prevent="handleLogin"
          >
            <span>{{ loading ? '正在登录…' : '登录控制台' }}</span>
            <span v-if="!loading" class="submit-arrow">→</span>
          </el-button>
        </el-form>

        <el-form v-else class="login-form mfa-form" @submit.prevent="handleMfaVerify">
          <el-form-item>
            <label class="field-label" for="login-mfa-code">身份验证器验证码或恢复码</label>
            <el-input
              id="login-mfa-code"
              v-model="mfaCode"
              size="large"
              maxlength="12"
              auto-complete="one-time-code"
              placeholder="6 位验证码或 12 位恢复码"
              @keyup.enter="handleMfaVerify"
            >
              <template #prefix>
                <svg-icon icon-class="validCode" class="input-icon" />
              </template>
            </el-input>
          </el-form-item>
          <p class="mfa-hint">恢复码验证成功后会立即失效，且每枚只能使用一次。</p>
          <el-button
            :loading="mfaVerifying"
            :disabled="mfaVerifying"
            size="large"
            type="primary"
            native-type="submit"
            class="submit-button"
          >
            <span>{{ mfaVerifying ? '正在验证…' : '验证并登录' }}</span>
            <span v-if="!mfaVerifying" class="submit-arrow">→</span>
          </el-button>
          <el-button link class="mfa-back" @click="resetMfaChallenge">返回账号密码登录</el-button>
        </el-form>

        <div v-if="register && !mfaChallengeToken" class="register-card">
          <div>
            <span class="register-caption">还没有商户账号？</span>
            <strong>从个人商户入驻开始</strong>
          </div>
          <router-link to="/register" class="register-link">
            立即注册
            <span aria-hidden="true">→</span>
          </router-link>
        </div>

        <div v-if="socialLoginEnabled && !mfaChallengeToken" class="divider">
          <span>或使用第三方账号登录</span>
        </div>
        <div v-if="socialLoginEnabled && !mfaChallengeToken" class="social-actions">
          <el-button circle :title="$t('login.social.wechat')" @click="doSocialLogin('wechat')">
            <svg-icon icon-class="wechat" />
          </el-button>
          <el-button circle :title="$t('login.social.maxkey')" @click="doSocialLogin('maxkey')">
            <svg-icon icon-class="maxkey" />
          </el-button>
          <el-button circle :title="$t('login.social.topiam')" @click="doSocialLogin('topiam')">
            <svg-icon icon-class="topiam" />
          </el-button>
          <el-button circle :title="$t('login.social.gitee')" @click="doSocialLogin('gitee')">
            <svg-icon icon-class="gitee" />
          </el-button>
          <el-button circle :title="$t('login.social.github')" @click="doSocialLogin('github')">
            <svg-icon icon-class="github" />
          </el-button>
        </div>

        <p class="auth-note">
          <span class="note-icon">⌁</span>
          登录即表示您同意平台服务协议与隐私政策
        </p>
      </section>
    </main>

    <footer class="login-footer">
      <span>© LuLuPay · 码支付</span>
      <span class="footer-separator">·</span>
      <span>专注于可靠的收款确认</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { to } from 'await-to-js';
import { useI18n } from 'vue-i18n';
import { getCodeImg } from '@/api/login';
import { acceptMerchantInvitation } from '@/api/payment';
import { authRouterUrl } from '@/api/system/social/auth';
import type { LoginData, LoginResult } from '@/api/types';
import { HttpStatus } from '@/enums/RespEnum';
import { useUserStore } from '@/store/modules/user';
import { isApiCryptoV2Available, isApiCryptoV2Enabled } from '@/utils/apiCryptoV2';
import { normalizeInternalRedirect } from '@/utils/navigation';
import luluPayLogo from '@/assets/logo/logo.png';

const userStore = useUserStore();
const router = useRouter();
const route = useRoute();
const { t } = useI18n();
const loginRef = ref<ElFormInstance>();
const codeUrl = ref('');
const loading = ref(false);
const captchaEnabled = ref(true);
const register = true;
const socialLoginEnabled = false;
const redirect = ref('/index');
const mfaChallengeToken = ref('');
const mfaCode = ref('');
const mfaVerifying = ref(false);
const insecureLoginMessage = '当前访问环境暂不支持安全登录，请使用安全连接后重试';
const apiCryptoUnavailable = computed(
  () => isApiCryptoV2Enabled() && !isApiCryptoV2Available()
);

const loginForm = ref<LoginData>({
  username: '',
  password: '',
  rememberMe: false,
  code: '',
  uuid: ''
} as LoginData);

const loginRules: ElFormRules = {
  username: [
    {
      required: true,
      trigger: 'blur',
      message: t('login.rule.username.required')
    }
  ],
  password: [
    {
      required: true,
      trigger: 'blur',
      message: t('login.rule.password.required')
    }
  ],
  code: [
    {
      required: true,
      trigger: 'change',
      message: t('login.rule.code.required')
    }
  ]
};

watch(
  () => router.currentRoute.value,
  (newRoute: any) => {
    redirect.value = normalizeInternalRedirect(newRoute.query?.redirect);
  },
  { immediate: true }
);

const completeLogin = async () => {
  const invitationToken =
    typeof route.query.invitationToken === 'string' ? route.query.invitationToken : '';
  if (invitationToken) {
    await acceptMerchantInvitation(invitationToken);
    ElMessage.success('已接受商户邀请');
  }
  await router.push(normalizeInternalRedirect(redirect.value));
};

const handleLoginResult = async (result: LoginResult) => {
  if (result.mfaRequired && result.mfaChallengeToken) {
    mfaChallengeToken.value = result.mfaChallengeToken;
    mfaCode.value = '';
    await nextTick();
    document.querySelector<HTMLInputElement>('#login-mfa-code')?.focus();
    return;
  }
  await completeLogin();
};

const handleLogin = () => {
  if (loading.value) return;
  if (apiCryptoUnavailable.value) {
    ElMessage.warning(insecureLoginMessage);
    return;
  }
  loginRef.value?.validate(async (valid: boolean, fields: any) => {
    if (!valid) {
      console.log('login validation failed', fields);
      return;
    }

    loading.value = true;
    try {
      if (loginForm.value.rememberMe) {
        localStorage.setItem('username', String(loginForm.value.username));
        localStorage.setItem('rememberMe', 'true');
      } else {
        localStorage.removeItem('username');
        localStorage.removeItem('rememberMe');
      }
      localStorage.removeItem('password');

      const [err, result] = await to(userStore.login(loginForm.value));
      if (!err && result) {
        await handleLoginResult(result);
      } else if (captchaEnabled.value) {
        await getCode();
      }
    } finally {
      loading.value = false;
    }
  });
};

const handleMfaVerify = async () => {
  if (apiCryptoUnavailable.value) {
    ElMessage.warning(insecureLoginMessage);
    return;
  }
  const code = mfaCode.value.trim();
  if (!/^(?:\d{6}|[A-Fa-f0-9]{12})$/.test(code)) {
    ElMessage.warning('请输入 6 位身份验证器验证码或 12 位恢复码');
    return;
  }
  if (mfaVerifying.value) return;
  mfaVerifying.value = true;
  try {
    const [err] = await to(userStore.verifyMfa(mfaChallengeToken.value, code));
    if (!err) {
      await completeLogin();
    }
  } finally {
    mfaVerifying.value = false;
  }
};

const resetMfaChallenge = async () => {
  mfaChallengeToken.value = '';
  mfaCode.value = '';
  if (captchaEnabled.value) {
    await getCode();
  }
};

const getCode = async () => {
  const response = await getCodeImg();
  const { data } = response;
  captchaEnabled.value = data.captchaEnabled === undefined ? true : data.captchaEnabled;
  if (captchaEnabled.value) {
    loginForm.value.code = '';
    codeUrl.value = `data:image/gif;base64,${data.img}`;
    loginForm.value.uuid = data.uuid;
  }
};

const getLoginData = () => {
  const account = typeof route.query.account === 'string' ? route.query.account : '';
  const username = localStorage.getItem('username');
  const rememberMe = localStorage.getItem('rememberMe');
  loginForm.value = {
    username: account || username || '',
    password: '',
    rememberMe: rememberMe === 'true',
    code: '',
    uuid: ''
  } as LoginData;
};

const doSocialLogin = (type: string) => {
  authRouterUrl(type).then((response: any) => {
    if (response.code === HttpStatus.SUCCESS) {
      window.location.href = response.data;
    } else {
      ElMessage.error(response.msg);
    }
  });
};

onMounted(async () => {
  getLoginData();
  await getCode();
  if (route.query.registered === '1') {
    ElMessage.success('账号注册成功，请使用用户名或邮箱登录');
  }
  if (route.query.passwordReset === '1') {
    ElMessage.success('密码已重置，请使用新密码登录');
  }
});
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  min-height: 100vh;
  min-height: 100dvh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22px 24px 52px;
  color: #eaf2ff;
  background:
    radial-gradient(circle at 12% 16%, rgba(68, 105, 255, 0.22), transparent 26%),
    radial-gradient(circle at 88% 78%, rgba(21, 195, 209, 0.17), transparent 30%),
    linear-gradient(135deg, #07111f 0%, #0c1830 48%, #112e50 100%);
}

.ambient {
  position: absolute;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  filter: blur(4px);
  pointer-events: none;
  opacity: 0.6;
}

.ambient-one {
  top: -180px;
  left: 18%;
  background: rgba(94, 92, 255, 0.24);
}

.ambient-two {
  right: -150px;
  bottom: 8%;
  background: rgba(28, 209, 214, 0.18);
}

.ambient-three {
  left: -210px;
  bottom: -180px;
  background: rgba(52, 102, 255, 0.14);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(390px, 460px);
  align-items: start;
  gap: 22px;
}

.mfa-form {
  .mfa-hint {
    margin: -4px 0 18px;
    color: rgba(215, 230, 250, 0.66);
    font-size: 12px;
    line-height: 1.7;
  }

  .mfa-back {
    width: 100%;
    margin: 12px 0 0;
    color: rgba(218, 231, 251, 0.72);
  }
}

.brand-panel,
.auth-panel {
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 30px;
  box-shadow: 0 30px 90px rgba(1, 8, 22, 0.34);
  backdrop-filter: blur(20px);
}

.brand-panel {
  box-sizing: border-box;
  height: min(664px, calc(100dvh - 100px));
  min-height: 620px;
  padding: 30px 36px 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.025)),
    linear-gradient(145deg, rgba(54, 95, 255, 0.24), rgba(9, 25, 53, 0.48));
}

.brand-header {
  display: flex;
  align-items: center;
  gap: 13px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 15px;
  object-fit: cover;
  box-shadow: 0 12px 26px rgba(54, 109, 255, 0.34);
}

.brand-kicker,
.auth-eyebrow {
  display: block;
  color: rgba(207, 224, 255, 0.66);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.auth-home-link {
  display: inline-flex;
  margin-bottom: 14px;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.brand-name {
  display: block;
  margin-top: 4px;
  color: #fff;
  font-size: 16px;
  letter-spacing: 0.04em;
}

.brand-content {
  max-width: 560px;
  margin-top: 34px;
}

.brand-overline {
  color: #6ee7e4;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.brand-content h1 {
  margin: 14px 0 16px;
  color: #f8fbff;
  font-size: clamp(42px, 5vw, 66px);
  line-height: 1.06;
  letter-spacing: -0.05em;

  em {
    color: #8ce9e7;
    font-style: normal;
  }
}

.brand-content p {
  max-width: 520px;
  margin: 0;
  color: rgba(225, 236, 252, 0.76);
  font-size: 15px;
  line-height: 1.75;
}

.brand-flow {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 30px;
}

.flow-line {
  position: absolute;
  top: 15px;
  right: 15%;
  left: 15%;
  height: 1px;
  background: linear-gradient(90deg, rgba(119, 141, 255, 0.45), rgba(110, 231, 228, 0.5));
}

.flow-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 6px;

  strong {
    margin-top: 18px;
    color: #f5f9ff;
    font-size: 15px;
  }

  small {
    color: rgba(215, 230, 250, 0.58);
    font-size: 12px;
  }
}

.flow-index {
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(139, 233, 231, 0.46);
  border-radius: 50%;
  color: #92ece7;
  font-size: 11px;
  font-weight: 800;
  background: rgba(12, 34, 60, 0.82);
}

.brand-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 24px;
  color: rgba(215, 230, 250, 0.58);
  font-size: 12px;
}

.status-dot,
.security-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #68e1d7;
  box-shadow: 0 0 0 5px rgba(104, 225, 215, 0.1);
}

.auth-panel {
  box-sizing: border-box;
  padding: 26px 30px 20px;
  color: var(--app-text-title);
  background: rgba(248, 251, 255, 0.96);
}

.auth-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.auth-eyebrow {
  color: #6475c9;
}

.auth-header h2 {
  margin: 6px 0 4px;
  color: #17243d;
  font-size: 28px;
  letter-spacing: -0.04em;
}

.auth-header p {
  margin: 0;
  color: #78859b;
  font-size: 13px;
  line-height: 1.55;
}

.auth-header :deep(.lang-select--style) {
  padding: 7px;
  border-radius: 12px;
  background: #f2f5fb;
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  :deep(.el-form-item__error) {
    padding-top: 5px;
  }
}

.field-label {
  display: block;
  width: 100%;
  margin-bottom: 5px;
  color: #34435e;
  font-size: 13px;
  font-weight: 700;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 12px;
  background: #f8faff;
  box-shadow: 0 0 0 1px #e4eaf5 inset;
  transition: 0.2s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd6ec inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(80, 101, 222, 0.48) inset,
    0 0 0 4px rgba(80, 101, 222, 0.1);
}

.input-icon {
  width: 15px;
  height: 15px;
  color: #92a0b8;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 122px;
  gap: 10px;
}

.captcha-image {
  min-width: 0;
  height: 44px;
  padding: 0;
  overflow: hidden;
  border: 1px solid #e4eaf5;
  border-radius: 12px;
  color: #73829b;
  background: #f8faff;
  cursor: pointer;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 14px;
}

.form-options :deep(.el-checkbox__label) {
  color: #7b889e;
  font-size: 12px;
}

.forgot-link {
  color: #5f72ff;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.forgot-link:hover {
  color: #21b9bd;
}

.security-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #7b889e;
  font-size: 12px;
}

.security-dot {
  width: 6px;
  height: 6px;
  box-shadow: 0 0 0 4px rgba(104, 225, 215, 0.12);
}

.submit-button {
  width: 100%;
  height: 46px;
  border: 0;
  border-radius: 13px;
  font-size: 15px;
  font-weight: 700;
  background: linear-gradient(110deg, #5267df, #6279ed 48%, #28bfc4);
  box-shadow: 0 16px 28px rgba(78, 104, 220, 0.24);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.submit-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 20px 34px rgba(78, 104, 220, 0.3);
}

.submit-arrow,
.register-link span {
  margin-left: 10px;
  font-size: 18px;
}

.register-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 14px;
  padding: 10px 14px;
  border: 1px solid #dfe7f6;
  border-radius: 16px;
  background: linear-gradient(135deg, #f5f8ff, #f1fbfb);
}

.register-caption,
.register-card strong {
  display: block;
}

.register-caption {
  color: #8a96aa;
  font-size: 11px;
}

.register-card strong {
  margin-top: 4px;
  color: #30405e;
  font-size: 13px;
}

.register-link {
  flex: none;
  color: #4f63d8;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.register-link:hover {
  color: #2daeb6;
}

.divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 14px 0 10px;
  color: #a0aabd;
  font-size: 11px;

  &::before,
  &::after {
    flex: 1;
    height: 1px;
    content: '';
    background: #e7ecf5;
  }
}

.social-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.social-actions :deep(.el-button.is-circle) {
  width: 34px;
  height: 34px;
  border-color: #e2e8f3;
  color: #8190aa;
  background: #f8faff;
}

.social-actions :deep(.el-button.is-circle:hover) {
  border-color: #bfcaf0;
  color: #5267df;
  background: #eef2ff;
}

.auth-note {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin: 12px 0 0;
  color: #a1acbd;
  font-size: 11px;
}

.note-icon {
  color: #52bfc1;
  font-size: 14px;
}

.login-footer {
  position: absolute;
  right: 24px;
  bottom: 14px;
  left: 24px;
  z-index: 1;
  display: flex;
  justify-content: center;
  gap: 10px;
  color: rgba(216, 229, 249, 0.54);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.footer-separator {
  color: rgba(110, 231, 228, 0.6);
}

@media (max-width: 960px) {
  .login-page {
    align-items: flex-start;
    height: auto;
    min-height: 100dvh;
    overflow-y: auto;
    padding: 22px 16px 64px;
  }

  .login-shell {
    grid-template-columns: 1fr;
    max-width: 520px;
  }

  .brand-panel {
    display: none;
  }
}

@media (max-width: 640px) {
  .login-page {
    align-items: flex-start;
    height: auto;
    padding: 18px 12px 74px;
  }

  .brand-panel {
    display: none;
  }

  .auth-panel {
    padding: 28px 18px 24px;
    border-radius: 24px;
  }

  .auth-header {
    margin-bottom: 24px;
  }

  .auth-header h2 {
    font-size: 27px;
  }

  .captcha-row {
    grid-template-columns: 1fr;
  }

  .captcha-image {
    width: 122px;
  }

  .register-card {
    align-items: flex-start;
    flex-direction: column;
  }

  .login-footer {
    flex-wrap: wrap;
    bottom: 18px;
    font-size: 10px;
  }
}

@media (min-width: 961px) and (max-height: 767px) {
  .login-page {
    align-items: flex-start;
    height: auto;
    min-height: 100dvh;
    overflow-y: auto;
    padding-top: 18px;
  }

  .brand-panel {
    height: auto;
    min-height: 620px;
  }
}
</style>
