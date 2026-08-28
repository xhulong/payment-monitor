<template>
  <div class="register-page">
    <div class="register-glow glow-one"></div>
    <div class="register-glow glow-two"></div>

    <main class="register-shell">
      <section class="register-intro">
        <router-link to="/login" class="back-link">
          <span aria-hidden="true">←</span>
          返回登录
        </router-link>

        <div class="intro-brand">
          <img :src="luluPayLogo" class="brand-mark" alt="LuLuPay Logo" />
          <div>
            <span>码支付</span>
            <strong>LuLuPay</strong>
          </div>
        </div>

        <div class="intro-copy">
          <span class="eyebrow">MERCHANT ONBOARDING</span>
          <h1>从今天开始，<br /><em>管理您的收款。</em></h1>
          <p>
            注册个人商户账号，提交基本资料，审核通过后即可连接自己的微信或支付宝收款设备。
          </p>
        </div>

        <div class="benefit-list">
          <div class="benefit-item">
            <span class="benefit-icon">01</span>
            <div>
              <strong>邮箱验证</strong>
              <small>保护账号安全，找回登录入口</small>
            </div>
          </div>
          <div class="benefit-item">
            <span class="benefit-icon">02</span>
            <div>
              <strong>资料审核</strong>
              <small>只需填写经营场景等基础信息</small>
            </div>
          </div>
          <div class="benefit-item">
            <span class="benefit-icon">03</span>
            <div>
              <strong>设备接入</strong>
              <small>配对手机后开始接收支付通知</small>
            </div>
          </div>
        </div>
      </section>

      <section class="register-panel">
        <div class="panel-heading">
          <span class="eyebrow">CREATE ACCOUNT</span>
          <h2>创建个人商户账号</h2>
          <p>先完成账号注册，登录后再提交商户入驻资料。</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="register-form">
          <div class="form-grid">
            <el-form-item label="登录用户名" prop="username">
              <el-input
                v-model="form.username"
                size="large"
                maxlength="30"
                placeholder="如 merchant_admin"
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" size="large" maxlength="30" placeholder="用于后台显示" />
            </el-form-item>
          </div>

          <el-form-item label="邮箱地址" prop="email">
            <el-input v-model="form.email" type="email" size="large" placeholder="you@example.com" />
          </el-form-item>

          <el-form-item v-if="showCaptcha" label="图形验证码" prop="captchaCode">
            <div class="captcha-row">
              <el-input v-model="form.captchaCode" size="large" placeholder="输入图片中的验证码" />
              <button type="button" class="captcha-image" aria-label="刷新图形验证码" @click="loadCaptcha">
                <img v-if="codeUrl" :src="codeUrl" alt="图形验证码" />
                <span v-else>加载中</span>
              </button>
            </div>
          </el-form-item>

          <el-form-item label="邮箱验证码" prop="emailCode">
            <div class="email-code-row">
              <el-input v-model="form.emailCode" size="large" placeholder="输入 6 位邮箱验证码" />
              <el-button
                size="large"
                :loading="sendingCode"
                :disabled="countdown > 0"
                @click="handleEmailCodeAction"
              >
                {{ emailCodeActionLabel }}
              </el-button>
            </div>
            <div v-if="emailCodeIssued && !showCaptcha" class="email-code-sent">
              验证码已发送至 {{ issuedEmail }}，注册时无需再次输入图形验证码。
            </div>
          </el-form-item>

          <div class="form-grid">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                size="large"
                show-password
                placeholder="12–64 位密码"
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                size="large"
                show-password
                placeholder="再次输入密码"
              />
            </el-form-item>
          </div>

          <div class="agreement-note">
            用户名支持 4–30 位字母、数字和下划线，必须以字母开头。注册后可使用用户名或邮箱登录。
          </div>

          <el-button
            class="submit-button"
            type="primary"
            size="large"
            :loading="loading"
            :disabled="loading"
            @click="submit"
          >
            注册并进入入驻向导
            <span aria-hidden="true">→</span>
          </el-button>
        </el-form>

        <div class="panel-footer">
          已有账号？
          <router-link to="/login">返回登录</router-link>
        </div>
        <p class="brand-footer">© LuLuPay · 码支付</p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { getCodeImg } from '@/api/login';
import {
  registerMerchantAccount,
  sendMerchantRegistrationEmailCode
} from '@/api/payment';
import luluPayLogo from '@/assets/logo/logo.png';

const router = useRouter();
const route = useRoute();
const formRef = ref<ElFormInstance>();
const loading = ref(false);
const sendingCode = ref(false);
const codeUrl = ref('');
const countdown = ref(0);
const emailCodeIssued = ref(false);
const issuedEmail = ref('');
const showCaptcha = ref(true);
const captchaValidationRequired = ref(false);
let countdownTimer: number | undefined;

const form = reactive({
  username: '',
  email: '',
  captchaUuid: '',
  captchaCode: '',
  emailCode: '',
  nickname: '',
  password: '',
  confirmPassword: ''
});

const rules: ElFormRules = {
  username: [
    { required: true, message: '请输入登录用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_]{3,29}$/,
      message: '4–30 位，以字母开头，只能包含字母、数字和下划线',
      trigger: 'blur'
    }
  ],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱地址', trigger: 'blur' }],
  captchaCode: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (captchaValidationRequired.value && !String(value || '').trim()) {
          callback(new Error('请输入图形验证码'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ],
  emailCode: [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }],
  nickname: [{ required: true, min: 1, max: 30, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, min: 12, max: 64, message: '密码长度必须为 12–64 位', trigger: 'blur' }],
  confirmPassword: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) =>
        value === form.password ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
};

const loadCaptcha = async () => {
  const response = await getCodeImg();
  const data = response.data;
  codeUrl.value = data?.img ? `data:image/gif;base64,${data.img}` : '';
  form.captchaUuid = data?.uuid || '';
};

const emailCodeActionLabel = computed(() => {
  if (countdown.value > 0) {
    return `${countdown.value}s 后重试`;
  }
  if (emailCodeIssued.value && !showCaptcha.value) {
    return '重新发送';
  }
  return emailCodeIssued.value ? '确认重发' : '发送验证码';
});

const prepareResend = async () => {
  showCaptcha.value = true;
  form.captchaCode = '';
  await loadCaptcha();
  await nextTick();
  formRef.value?.clearValidate('captchaCode');
};

const handleEmailCodeAction = async () => {
  if (emailCodeIssued.value && !showCaptcha.value) {
    await prepareResend();
    return;
  }
  await sendCode();
};

const sendCode = async () => {
  if (sendingCode.value || countdown.value > 0) return;
  sendingCode.value = true;
  let requestStarted = false;
  let requestSucceeded = false;
  captchaValidationRequired.value = true;
  try {
    await formRef.value?.validateField(['email', 'captchaCode']);
    requestStarted = true;
    const normalizedEmail = form.email.trim().toLowerCase();
    await sendMerchantRegistrationEmailCode({
      email: normalizedEmail,
      captchaUuid: form.captchaUuid,
      captchaCode: form.captchaCode
    });
    requestSucceeded = true;
    emailCodeIssued.value = true;
    issuedEmail.value = normalizedEmail;
    showCaptcha.value = false;
    codeUrl.value = '';
    form.captchaUuid = '';
    ElMessage.success('验证码已发送，请检查邮箱');
    countdown.value = 60;
    window.clearInterval(countdownTimer);
    countdownTimer = window.setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        window.clearInterval(countdownTimer);
        countdownTimer = undefined;
      }
    }, 1000);
  } finally {
    sendingCode.value = false;
    captchaValidationRequired.value = false;
    if (requestStarted) {
      form.captchaCode = '';
      if (!requestSucceeded) {
        await loadCaptcha();
      }
    }
    await nextTick();
    formRef.value?.clearValidate('captchaCode');
  }
};

const submit = async () => {
  if (loading.value) return;
  loading.value = true;
  try {
    await formRef.value?.validate();
    const response = await registerMerchantAccount({
      username: form.username.trim().toLowerCase(),
      email: form.email.trim().toLowerCase(),
      nickname: form.nickname.trim(),
      password: form.password,
      emailCode: form.emailCode
    });
    const data = response.data!;
    ElMessage.success(`注册成功，您的用户名是 ${data.username}`);
    await router.replace({
      path: '/login',
      query: {
        account: data.email,
        registered: '1',
        ...(route.query.invitationToken
          ? { invitationToken: String(route.query.invitationToken) }
          : {})
      }
    });
  } finally {
    loading.value = false;
  }
};

watch(
  () => form.email,
  async value => {
    if (
      !emailCodeIssued.value ||
      value.trim().toLowerCase() === issuedEmail.value
    ) {
      return;
    }
    emailCodeIssued.value = false;
    issuedEmail.value = '';
    showCaptcha.value = true;
    form.emailCode = '';
    form.captchaCode = '';
    countdown.value = 0;
    window.clearInterval(countdownTimer);
    countdownTimer = undefined;
    await loadCaptcha();
  }
);

onMounted(loadCaptcha);
onBeforeUnmount(() => window.clearInterval(countdownTimer));
</script>

<style scoped lang="scss">
.register-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background:
    radial-gradient(circle at 10% 10%, rgba(72, 99, 230, 0.22), transparent 28%),
    radial-gradient(circle at 92% 88%, rgba(37, 201, 202, 0.17), transparent 30%),
    linear-gradient(135deg, #07111f, #0c1d38 55%, #102e4a);
}

.register-glow {
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  filter: blur(8px);
  opacity: 0.6;
}

.glow-one {
  top: -160px;
  right: 20%;
  background: rgba(100, 92, 255, 0.22);
}

.glow-two {
  bottom: -180px;
  left: 15%;
  background: rgba(35, 208, 207, 0.14);
}

.register-shell {
  position: relative;
  z-index: 1;
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(500px, 1.1fr);
  gap: 22px;
}

.register-intro,
.register-panel {
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 28px;
  box-shadow: 0 30px 90px rgba(1, 8, 22, 0.34);
}

.register-intro {
  padding: 30px 34px;
  display: flex;
  flex-direction: column;
  color: #eef6ff;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.025)),
    linear-gradient(145deg, rgba(56, 91, 231, 0.24), rgba(8, 25, 52, 0.52));
  backdrop-filter: blur(20px);
}

.back-link {
  align-self: flex-start;
  color: rgba(224, 237, 255, 0.72);
  font-size: 13px;
  text-decoration: none;
}

.back-link:hover {
  color: #8ce9e7;
}

.intro-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 44px;

  span,
  strong {
    display: block;
  }

  span {
    color: rgba(207, 224, 255, 0.62);
    font-size: 10px;
    font-weight: 800;
    letter-spacing: 0.14em;
  }

  strong {
    margin-top: 4px;
    font-size: 16px;
  }
}

.brand-mark,
.benefit-icon {
  display: grid;
  place-items: center;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, #6d73ff, #21d3d1);
}

.intro-copy {
  margin-top: auto;
  padding: 76px 0 52px;
}

.eyebrow {
  color: #6ee7e4;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.intro-copy h1 {
  margin: 16px 0;
  color: #f8fbff;
  font-size: clamp(36px, 4vw, 54px);
  line-height: 1.08;
  letter-spacing: -0.05em;

  em {
    color: #8ce9e7;
    font-style: normal;
  }
}

.intro-copy p {
  margin: 0;
  color: rgba(225, 236, 252, 0.72);
  font-size: 14px;
  line-height: 1.9;
}

.benefit-list {
  display: grid;
  gap: 15px;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.benefit-icon {
  width: 31px;
  height: 31px;
  flex: none;
  border: 1px solid rgba(139, 233, 231, 0.4);
  border-radius: 50%;
  color: #8ce9e7;
  font-size: 10px;
  font-weight: 800;
  background: rgba(12, 34, 60, 0.82);
}

.benefit-item strong,
.benefit-item small {
  display: block;
}

.benefit-item strong {
  color: #f2f7ff;
  font-size: 13px;
}

.benefit-item small {
  margin-top: 3px;
  color: rgba(215, 230, 250, 0.54);
  font-size: 11px;
}

.register-panel {
  padding: 34px 34px 24px;
  background: rgba(248, 251, 255, 0.97);
}

.panel-heading h2 {
  margin: 9px 0 7px;
  color: #17243d;
  font-size: 28px;
  letter-spacing: -0.04em;
}

.panel-heading p {
  margin: 0 0 25px;
  color: #7b889e;
  font-size: 13px;
}

.panel-heading .eyebrow {
  color: #6475c9;
}

.register-form :deep(.el-form-item) {
  margin-bottom: 17px;
}

.register-form :deep(.el-form-item__label) {
  color: #34435e;
  font-size: 12px;
  font-weight: 700;
}

.register-form :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 13px;
  background: #f8faff;
  box-shadow: 0 0 0 1px #e4eaf5 inset;
}

.register-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(80, 101, 222, 0.48) inset,
    0 0 0 4px rgba(80, 101, 222, 0.1);
}

.captcha-row,
.email-code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 122px;
  gap: 10px;
}

.email-code-row {
  grid-template-columns: minmax(0, 1fr) 124px;
}

.email-code-sent {
  width: 100%;
  margin-top: 8px;
  padding: 9px 11px;
  border: 1px solid #cceee8;
  border-radius: 10px;
  color: #31837a;
  background: #effaf8;
  font-size: 11px;
  line-height: 1.6;
}

.captcha-image {
  min-width: 0;
  height: 48px;
  padding: 0;
  overflow: hidden;
  border: 1px solid #e4eaf5;
  border-radius: 13px;
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

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.agreement-note {
  margin: 1px 0 17px;
  color: #94a0b3;
  font-size: 11px;
  line-height: 1.7;
}

.submit-button {
  width: 100%;
  height: 50px;
  border: 0;
  border-radius: 14px;
  font-weight: 700;
  background: linear-gradient(110deg, #5267df, #6279ed 48%, #28bfc4);
  box-shadow: 0 16px 28px rgba(78, 104, 220, 0.24);
}

.submit-button span {
  margin-left: 10px;
  font-size: 18px;
}

.panel-footer {
  margin-top: 20px;
  color: #929eb0;
  font-size: 12px;
  text-align: center;

  a {
    margin-left: 5px;
    color: #5267df;
    font-weight: 700;
    text-decoration: none;
  }
}

.brand-footer {
  margin: 14px 0 0;
  color: #a0aabd;
  font-size: 11px;
  text-align: center;
}

@media (max-width: 860px) {
  .register-shell {
    grid-template-columns: 1fr;
    max-width: 620px;
  }

  .register-intro {
    display: none;
  }
}

@media (max-width: 560px) {
  .register-page {
    align-items: flex-start;
    padding: 16px 12px;
  }

  .register-panel {
    padding: 28px 18px 22px;
    border-radius: 24px;
  }

  .form-grid,
  .captcha-row,
  .email-code-row {
    grid-template-columns: 1fr;
  }

  .captcha-image {
    width: 122px;
  }
}
</style>
