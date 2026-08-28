<template>
  <div class="recovery-page">
    <div class="recovery-card">
      <div class="brand">
        <img :src="luluPayLogo" alt="LuLuPay Logo" />
        <div>
          <strong>LuLuPay</strong>
          <span>码支付</span>
        </div>
      </div>

      <div class="heading">
        <el-button link class="back-link" @click="router.push('/login')">← 返回登录</el-button>
        <h1>找回登录密码</h1>
        <p>
          {{
            stage === 'request'
              ? '验证邮箱后，我们会发送一封密码重置验证码邮件。'
              : `验证码已发送至 ${maskedEmail}，请输入验证码并设置新密码。`
          }}
        </p>
      </div>

      <el-form
        v-if="stage === 'request'"
        ref="requestRef"
        :model="requestForm"
        :rules="requestRules"
        label-position="top"
      >
        <el-form-item label="登录邮箱" prop="email">
          <el-input v-model="requestForm.email" size="large" placeholder="请输入登录邮箱" />
        </el-form-item>
        <el-form-item label="图片验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="requestForm.captchaCode"
              size="large"
              maxlength="8"
              placeholder="请输入图片验证码"
              @keyup.enter="sendCode"
            />
            <button type="button" class="captcha" aria-label="刷新验证码" @click="loadCaptcha">
              <img v-if="captchaUrl" :src="captchaUrl" alt="图片验证码" />
              <span v-else>加载中</span>
            </button>
          </div>
        </el-form-item>
        <el-button class="primary-action" type="primary" size="large" :loading="sending" @click="sendCode">
          发送验证码
        </el-button>
      </el-form>

      <el-form
        v-else
        ref="confirmRef"
        :model="confirmForm"
        :rules="confirmRules"
        label-position="top"
      >
        <el-form-item label="邮件验证码" prop="code">
          <el-input v-model="confirmForm.code" size="large" maxlength="6" placeholder="请输入 6 位验证码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="confirmForm.newPassword"
            size="large"
            type="password"
            show-password
            placeholder="请输入 12–64 位新密码"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="confirmForm.confirmPassword"
            size="large"
            type="password"
            show-password
            placeholder="请再次输入新密码"
            @keyup.enter="confirmReset"
          />
        </el-form-item>
        <div class="actions">
          <el-button size="large" @click="restart">重新发送</el-button>
          <el-button type="primary" size="large" :loading="confirming" @click="confirmReset">
            重置密码
          </el-button>
        </div>
      </el-form>

      <p class="privacy-note">为了保护账号安全，发送结果不会提示该邮箱是否已注册。</p>
      <p class="brand-footer">© LuLuPay · 码支付</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getCodeImg } from '@/api/login';
import { confirmPasswordReset, sendPasswordResetCode } from '@/api/payment';
import luluPayLogo from '@/assets/logo/logo.png';
import { maskRecoveryEmail } from './forgot-password';

const router = useRouter();
const stage = ref<'request' | 'confirm'>('request');
const requestRef = ref<ElFormInstance>();
const confirmRef = ref<ElFormInstance>();
const captchaUrl = ref('');
const sending = ref(false);
const confirming = ref(false);

const requestForm = reactive({
  email: '',
  captchaUuid: '',
  captchaCode: ''
});

const confirmForm = reactive({
  code: '',
  newPassword: '',
  confirmPassword: ''
});

const requestRules: ElFormRules = {
  email: [
    { required: true, message: '请输入登录邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
  ],
  captchaCode: [{ required: true, message: '请输入图片验证码', trigger: 'blur' }]
};

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== confirmForm.newPassword) {
    callback(new Error('两次输入的密码不一致'));
    return;
  }
  callback();
};

const confirmRules: ElFormRules = {
  code: [
    { required: true, message: '请输入邮件验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位邮件验证码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 12, max: 64, message: '密码长度必须为 12–64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
};

const maskedEmail = computed(() => maskRecoveryEmail(requestForm.email));

const loadCaptcha = async () => {
  const response = await getCodeImg();
  requestForm.captchaUuid = response.data.uuid;
  requestForm.captchaCode = '';
  captchaUrl.value = `data:image/gif;base64,${response.data.img}`;
};

const sendCode = async () => {
  const valid = await requestRef.value?.validate().catch(() => false);
  if (!valid || sending.value) return;
  sending.value = true;
  try {
    await sendPasswordResetCode({
      email: requestForm.email.trim().toLowerCase(),
      captchaUuid: requestForm.captchaUuid,
      captchaCode: requestForm.captchaCode.trim()
    });
    stage.value = 'confirm';
    ElMessage.success('如该邮箱已注册，密码重置验证码将发送至邮箱');
  } catch {
    await loadCaptcha();
  } finally {
    sending.value = false;
  }
};

const confirmReset = async () => {
  const valid = await confirmRef.value?.validate().catch(() => false);
  if (!valid || confirming.value) return;
  confirming.value = true;
  try {
    const email = requestForm.email.trim().toLowerCase();
    await confirmPasswordReset({
      email,
      code: confirmForm.code.trim(),
      newPassword: confirmForm.newPassword
    });
    await router.replace({
      path: '/login',
      query: { account: email, passwordReset: '1' }
    });
  } finally {
    confirming.value = false;
  }
};

const restart = async () => {
  stage.value = 'request';
  confirmForm.code = '';
  confirmForm.newPassword = '';
  confirmForm.confirmPassword = '';
  await loadCaptcha();
};

onMounted(loadCaptcha);
</script>

<style scoped lang="scss">
.recovery-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 12%, rgba(95, 96, 246, 0.24), transparent 32%),
    radial-gradient(circle at 85% 85%, rgba(36, 198, 200, 0.18), transparent 30%),
    #0b1325;
}

.recovery-card {
  width: min(480px, 100%);
  padding: 30px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 28px 80px rgba(4, 12, 30, 0.35);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand img {
  width: 48px;
  height: 48px;
  border-radius: 15px;
}

.brand div {
  display: flex;
  flex-direction: column;
}

.brand strong {
  font-size: 20px;
}

.brand span,
.heading p,
.privacy-note {
  color: #7b879c;
  font-size: 13px;
}

.heading {
  margin: 26px 0 22px;
}

.heading h1 {
  margin: 10px 0 8px;
  color: #172033;
  font-size: 28px;
}

.heading p {
  margin: 0;
  line-height: 1.7;
}

.back-link {
  padding: 0;
}

.captcha-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 128px;
  gap: 10px;
}

.captcha {
  overflow: hidden;
  height: 40px;
  padding: 0;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.captcha img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.primary-action {
  width: 100%;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.privacy-note {
  margin: 22px 0 0;
  text-align: center;
}

.brand-footer {
  margin: 10px 0 0;
  color: #a0aabd;
  font-size: 11px;
  text-align: center;
}

@media (max-width: 520px) {
  .recovery-page {
    padding: 12px;
  }
  .recovery-card {
    padding: 22px 18px;
  }
}
</style>
