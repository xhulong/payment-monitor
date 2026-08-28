<template>
  <div class="profile-info-panel">
    <el-form ref="userRef" :model="userForm" :rules="rules" label-width="80px" class="profile-form">
    <el-form-item label="用户昵称" prop="nickName">
      <el-input v-model="userForm.nickName" maxlength="30" />
    </el-form-item>
    <el-form-item label="手机号码" prop="phoneNumber">
      <el-input v-model="userForm.phoneNumber" maxlength="11" />
    </el-form-item>
    <el-form-item label="邮箱" prop="email">
      <div class="email-field">
        <el-input :model-value="userForm.email" readonly />
        <el-button type="primary" plain @click="openEmailChange">更换登录邮箱</el-button>
      </div>
      <div class="field-tip">登录邮箱只能通过密码、MFA 和新邮箱验证码完成修改。</div>
    </el-form-item>
    <el-form-item label="性别">
      <el-radio-group v-model="userForm.gender">
        <el-radio v-for="dict in sys_user_gender" :key="dict.value" :value="dict.value">
          {{ dict.label }}
        </el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item class="profile-form__actions">
      <el-button type="primary" @click="submit">保存</el-button>
      <el-button @click="close">关闭</el-button>
    </el-form-item>
    </el-form>

    <el-dialog
      v-model="emailDialogVisible"
      title="更换登录邮箱"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetEmailDialog"
    >
    <el-steps :active="emailStage === 'request' ? 0 : 1" simple finish-status="success">
      <el-step title="验证身份" />
      <el-step title="验证新邮箱" />
    </el-steps>

    <el-form
      v-if="emailStage === 'request'"
      ref="emailRequestRef"
      :model="emailForm"
      :rules="emailRequestRules"
      label-position="top"
      class="email-change-form"
    >
      <el-form-item label="当前登录邮箱">
        <el-input :model-value="userForm.email" readonly />
      </el-form-item>
      <el-form-item label="当前密码" prop="password">
        <el-input
          v-model="emailForm.password"
          type="password"
          show-password
          placeholder="请输入当前登录密码"
        />
      </el-form-item>
      <el-form-item label="新登录邮箱" prop="newEmail">
        <el-input v-model="emailForm.newEmail" placeholder="请输入新的登录邮箱" />
      </el-form-item>
    </el-form>

    <el-form
      v-else
      ref="emailConfirmRef"
      :model="emailForm"
      :rules="emailConfirmRules"
      label-position="top"
      class="email-change-form"
    >
      <el-alert
        type="success"
        :closable="false"
        show-icon
        :title="`验证码已发送到 ${emailForm.newEmail}`"
      />
      <el-form-item label="邮件验证码" prop="code">
        <el-input
          v-model="emailForm.code"
          maxlength="6"
          placeholder="请输入 6 位邮件验证码"
          @keyup.enter="confirmEmailChangeAction"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emailDialogVisible = false">取消</el-button>
      <el-button
        v-if="emailStage === 'request'"
        type="primary"
        :loading="emailSending"
        @click="sendEmailCode"
      >
        验证并发送验证码
      </el-button>
      <el-button
        v-else
        type="primary"
        :loading="emailConfirming"
        @click="confirmEmailChangeAction"
      >
        确认更换并重新登录
      </el-button>
    </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  confirmEmailChange,
  sendEmailChangeCode
} from '@/api/payment';
import { updateUserProfile } from '@/api/system/user';
import type { UserProfileForm } from '@/api/system/user/types';
import modal from '@/plugins/modal';
import tab from '@/plugins/tab';
import { useMerchantStore } from '@/store/modules/merchant';
import { useDict } from '@/utils/dict';
import { removeToken } from '@/utils/auth';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import { propTypes } from '@/utils/propTypes';

const { sys_user_gender } = toRefs<any>(useDict('sys_user_gender'));
const props = defineProps({
  user: propTypes.any.isRequired
});
const userForm = computed(() => props.user);
const userRef = ref<ElFormInstance>();
const emailRequestRef = ref<ElFormInstance>();
const emailConfirmRef = ref<ElFormInstance>();
const emailDialogVisible = ref(false);
const emailSending = ref(false);
const emailConfirming = ref(false);
const emailStage = ref<'request' | 'confirm'>('request');
const router = useRouter();
const merchantStore = useMerchantStore();
const emailForm = reactive({
  password: '',
  newEmail: '',
  code: ''
});
const rule: ElFormRules = {
  nickName: [{ required: true, message: '用户昵称不能为空', trigger: 'blur' }],
  phoneNumber: [
    {
      required: true,
      message: '手机号码不能为空',
      trigger: 'blur'
    },
    {
      pattern: /^1[3456789][0-9]\d{8}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur'
    }
  ]
};
const rules = ref<ElFormRules>(rule);
const emailRequestRules: ElFormRules = {
  password: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newEmail: [
    { required: true, message: '请输入新登录邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] }
  ]
};
const emailConfirmRules: ElFormRules = {
  code: [
    { required: true, message: '请输入邮件验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位邮件验证码', trigger: 'blur' }
  ]
};

/** 提交按钮 */
const submit = () => {
  userRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      const profile: UserProfileForm = {
        nickName: props.user.nickName,
        phoneNumber: props.user.phoneNumber,
        gender: props.user.gender
      };
      await updateUserProfile(profile);
      modal.msgSuccess('修改成功');
    }
  });
};

const openEmailChange = () => {
  emailDialogVisible.value = true;
};

const sendEmailCode = async () => {
  const valid = await emailRequestRef.value?.validate().catch(() => false);
  if (!valid || emailSending.value) return;
  emailSending.value = true;
  try {
    const stepUpToken = await requestPaymentStepUp('EMAIL_CHANGE', '更换登录邮箱');
    await sendEmailChangeCode(
      {
        newEmail: emailForm.newEmail.trim().toLowerCase(),
        password: emailForm.password
      },
      stepUpToken
    );
    emailForm.newEmail = emailForm.newEmail.trim().toLowerCase();
    emailStage.value = 'confirm';
    ElMessage.success('新邮箱验证码已发送');
  } finally {
    emailSending.value = false;
  }
};

const confirmEmailChangeAction = async () => {
  const valid = await emailConfirmRef.value?.validate().catch(() => false);
  if (!valid || emailConfirming.value) return;
  emailConfirming.value = true;
  try {
    await confirmEmailChange({
      newEmail: emailForm.newEmail,
      code: emailForm.code.trim()
    });
    removeToken();
    merchantStore.clear();
    ElMessage.success('登录邮箱已修改，请使用新邮箱重新登录');
    await router.replace({
      path: '/login',
      query: { account: emailForm.newEmail }
    });
  } finally {
    emailConfirming.value = false;
  }
};

const resetEmailDialog = () => {
  emailStage.value = 'request';
  emailForm.password = '';
  emailForm.newEmail = '';
  emailForm.code = '';
};
/** 关闭按钮 */
const close = () => {
  tab.closePage();
};
</script>

<style lang="scss" scoped>
.profile-form {
  max-width: 520px;
}

.profile-form :deep(.el-input__wrapper) {
  border-radius: 12px;
}

.profile-form :deep(.el-radio-group) {
  gap: 16px;
}

.profile-form :deep(.el-button) {
  border-radius: 10px;
}

.email-field {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.field-tip {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.email-change-form {
  margin-top: 20px;
}

.email-change-form :deep(.el-alert) {
  margin-bottom: 18px;
}

@media (max-width: 560px) {
  .email-field {
    grid-template-columns: 1fr;
  }
}

.profile-form__actions :deep(.el-form-item__content) {
  display: flex;
  gap: 8px;
}
</style>
