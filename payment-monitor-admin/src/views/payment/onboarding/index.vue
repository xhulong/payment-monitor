<template>
  <div v-loading="loading" class="p-2 onboarding-page">
    <el-card shadow="never" class="hero-card">
      <div class="hero-main">
        <div>
          <p class="eyebrow">MERCHANT ONBOARDING</p>
          <h2>{{ status?.merchantName || '个人商户入驻' }}</h2>
          <p class="hero-description">
            {{ heroDescription }}
          </p>
        </div>
        <el-button :loading="loading" icon="Refresh" @click="load">刷新进度</el-button>
      </div>

      <el-descriptions class="hero-meta" :column="3">
        <el-descriptions-item label="已验证邮箱">
          {{ status?.verifiedEmail || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="申请状态">
          <el-tag :type="applicationTagType">
            {{ applicationStatusLabel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商户状态">
          <el-tag :type="lifecycleTagType">
            {{ merchantLifecycleLabel }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="progress-row">
        <span>整体进度</span>
        <el-progress
          :percentage="progress"
          :status="progress === 100 ? 'success' : undefined"
          :stroke-width="12"
        />
      </div>
    </el-card>

    <el-alert
      v-if="showReviewNote"
      class="mt-3 review-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="`审核意见：${status?.application?.reviewNote}`"
    />

    <el-card
      v-if="showApplicationForm"
      shadow="never"
      class="mt-3 content-card"
    >
      <template #header>
        <div class="card-header">
          <div>
            <strong>{{ reapplicationMode ? '重新提交商户申请' : applicationFormTitle }}</strong>
            <p>{{ applicationFormDescription }}</p>
          </div>
          <el-tag v-if="status?.application?.status === 'NEEDS_CHANGES'" type="warning">
            请按审核意见补充
          </el-tag>
        </div>
      </template>

      <el-form
        ref="applicationFormRef"
        :model="form"
        :rules="rules"
        label-position="top"
        status-icon
      >
        <section class="form-section">
          <div class="section-heading">
            <span class="section-index">1</span>
            <div>
              <h3>基本资料</h3>
              <p>用于审核申请主体和后续商户展示。</p>
            </div>
          </div>
          <el-row :gutter="18">
            <el-col :xs="24" :md="12">
              <el-form-item label="商户展示名称" prop="merchantDisplayName">
                <el-input
                  v-model="form.merchantDisplayName"
                  maxlength="120"
                  show-word-limit
                  placeholder="例如：华南测试门店"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="申请人称呼" prop="applicantName">
                <el-input
                  v-model="form.applicantName"
                  maxlength="80"
                  show-word-limit
                  placeholder="请输入联系人姓名或称呼"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="手机号（可选）" prop="phoneNumber">
                <el-input
                  v-model="form.phoneNumber"
                  maxlength="32"
                  placeholder="用于必要的审核联系"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="国家/地区" prop="countryRegion">
                <el-input
                  v-model="form.countryRegion"
                  maxlength="80"
                  placeholder="例如：中国"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="省份（可选）" prop="province">
                <el-input v-model="form.province" maxlength="80" placeholder="请输入省份" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="城市（可选）" prop="city">
                <el-input v-model="form.city" maxlength="80" placeholder="请输入城市" />
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <section class="form-section">
          <div class="section-heading">
            <span class="section-index">2</span>
            <div>
              <h3>经营与收款信息</h3>
              <p>说明通知确认能力的使用场景和预估规模。</p>
            </div>
          </div>
          <el-form-item label="收款使用场景" prop="paymentUseCase">
            <el-input
              v-model="form.paymentUseCase"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="请描述商品或服务、收款方式及通知确认的使用场景"
            />
          </el-form-item>
          <el-row :gutter="18">
            <el-col :xs="24" :md="12">
              <el-form-item label="预计月订单量" prop="monthlyOrderRange">
                <el-select v-model="form.monthlyOrderRange" style="width: 100%">
                  <el-option
                    v-for="item in monthlyOrderOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="预计月收款金额（元）" prop="monthlyAmountRange">
                <el-select v-model="form.monthlyAmountRange" style="width: 100%">
                  <el-option
                    v-for="item in monthlyAmountOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <section class="form-section">
          <div class="section-heading">
            <span class="section-index">3</span>
            <div>
              <h3>收款平台</h3>
              <p>至少选择一个需要监听支付通知的平台。</p>
            </div>
          </div>
          <el-form-item prop="plannedPlatforms">
            <el-checkbox-group v-model="form.plannedPlatforms" class="platform-options">
              <el-checkbox border value="WECHAT">微信支付通知</el-checkbox>
              <el-checkbox border value="ALIPAY">支付宝通知</el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </section>

        <section class="form-section agreement-section">
          <div class="section-heading">
            <span class="section-index">4</span>
            <div>
              <h3>协议确认</h3>
              <p>{{ agreementDescription }}</p>
            </div>
          </div>
          <el-checkbox v-model="agreementAccepted">
            我已阅读并同意服务协议 {{ agreementVersion }} 与隐私政策 {{ privacyVersion }}
          </el-checkbox>
        </section>
      </el-form>

      <div class="form-actions">
        <el-button
          v-if="reapplicationMode"
          :disabled="busy"
          @click="cancelReapplication"
        >
          取消
        </el-button>
        <el-button :loading="saving" :disabled="busy && !saving" @click="saveDraft">
          保存草稿
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="busy && !submitting"
          @click="submitApplication"
        >
          {{ submitButtonText }}
        </el-button>
      </div>
    </el-card>

    <el-card
      v-else-if="viewState.panel === 'APPLICATION_REVIEW'"
      shadow="never"
      class="mt-3 content-card"
    >
      <template #header>
        <div class="card-header">
          <div>
            <strong>{{ reviewPanelTitle }}</strong>
            <p>{{ reviewPanelDescription }}</p>
          </div>
          <el-tag :type="applicationTagType">{{ applicationStatusLabel }}</el-tag>
        </div>
      </template>
      <application-summary
        v-if="status?.application"
        :application="status.application"
      />
      <div v-if="viewState.canWithdraw" class="form-actions">
        <el-button type="warning" plain :loading="withdrawing" @click="withdrawApplication">
          撤回申请
        </el-button>
      </div>
    </el-card>

    <el-card
      v-else-if="viewState.panel === 'REJECTED'"
      shadow="never"
      class="mt-3 content-card result-card"
    >
      <el-result icon="error" title="本次申请未通过审核">
        <template #sub-title>
          <p>{{ status?.application?.reviewNote || '请查看审核意见并准备新的申请资料。' }}</p>
          <p v-if="!viewState.canReapply">
            可重新申请时间：{{ formatApiTime(status?.application?.cooldownUntil) }}
          </p>
          <p v-else>冷却期已结束，可以使用原资料重新发起申请。</p>
        </template>
        <template #extra>
          <el-button
            type="primary"
            :disabled="!viewState.canReapply"
            @click="startReapplication"
          >
            重新申请
          </el-button>
        </template>
      </el-result>
      <application-summary
        v-if="status?.application"
        :application="status.application"
      />
    </el-card>

    <el-card
      v-else-if="viewState.panel === 'REAPPLY'"
      shadow="never"
      class="mt-3 content-card result-card"
    >
      <el-result
        icon="warning"
        title="申请已撤回"
        sub-title="当前没有进行中的审核，可检查资料后重新申请。"
      >
        <template #extra>
          <el-button type="primary" @click="startReapplication">重新申请</el-button>
        </template>
      </el-result>
      <application-summary
        v-if="status?.application"
        :application="status.application"
      />
    </el-card>

    <el-card
      v-else-if="viewState.panel === 'ONBOARDING_TASKS'"
      shadow="never"
      class="mt-3 content-card"
    >
      <template #header>
        <div class="card-header">
          <div>
            <strong>完成商户开通任务</strong>
            <p>系统会在所有必需任务完成后自动将商户状态更新为 ACTIVE。</p>
          </div>
          <el-tag type="success">审核已通过</el-tag>
        </div>
      </template>
      <div class="task-grid">
        <article
          v-for="item in status?.checklist || []"
          :key="item.code"
          class="task-card"
          :class="{ completed: item.completed }"
        >
          <div class="task-icon">
            <el-icon>
              <CircleCheck v-if="item.completed" />
              <Clock v-else />
            </el-icon>
          </div>
          <div class="task-content">
            <div class="task-title-row">
              <strong>{{ item.label }}</strong>
              <el-tag v-if="!item.required" type="info" size="small">
                可选建议
              </el-tag>
              <el-tag :type="item.completed ? 'success' : 'warning'" size="small">
                {{ item.completed ? '已完成' : '待完成' }}
              </el-tag>
            </div>
            <p>{{ checklistMeta(item.code).description }}</p>
            <el-button
              v-if="checklistMeta(item.code).path"
              link
              type="primary"
              @click="navigateTo(checklistMeta(item.code).path!)"
            >
              {{ item.completed ? '查看' : checklistMeta(item.code).action }}
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </article>
      </div>
    </el-card>

    <el-card
      v-else-if="viewState.panel === 'ACTIVE'"
      shadow="never"
      class="mt-3 content-card result-card active-card"
    >
      <el-result
        icon="success"
        title="商户已正式开通"
        :sub-title="`${status?.merchantName || '当前商户'} 已完成全部开通任务，可以开始创建订单。`"
      >
        <template #extra>
          <el-button @click="navigateTo('/index')">进入首页</el-button>
          <el-button type="primary" @click="navigateTo(PAYMENT_ROUTES.order)">
            创建订单
          </el-button>
        </template>
      </el-result>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="商户名称">{{ status?.merchantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商户编号">{{ status?.merchantCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成员岗位">{{ merchantRoleLabel(status?.memberRole) }}</el-descriptions-item>
        <el-descriptions-item label="已验证邮箱">{{ status?.verifiedEmail || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup name="MerchantOnboarding" lang="ts">
import { ArrowRight, CircleCheck, Clock } from '@element-plus/icons-vue';
import ApplicationSummary from './application-summary.vue';
import {
  createMerchantApplication,
  getMerchantOnboardingStatus,
  submitMerchantApplication,
  updateMerchantApplication,
  withdrawMerchantApplication
} from '@/api/payment';
import { formatApiTime } from '@/api/payment/time';
import type {
  MerchantApplicationSaveForm,
  MerchantApplicationVO,
  MerchantOnboardingStatusVO,
  PlannedPaymentPlatform
} from '@/api/payment/types';
import { merchantRoleLabel } from '@/utils/merchant-role';
import { PAYMENT_ROUTES } from '@/utils/payment-routes';
import {
  applicationStatusLabels,
  calculateOnboardingProgress,
  deriveOnboardingViewState,
  merchantLifecycleLabels
} from './onboarding-state';

type MerchantApplicationEditorForm = Omit<
  MerchantApplicationSaveForm,
  'plannedPlatforms'
> & {
  plannedPlatforms: PlannedPaymentPlatform[];
};

const router = useRouter();
const status = ref<MerchantOnboardingStatusVO>();
const loading = ref(false);
const saving = ref(false);
const submitting = ref(false);
const withdrawing = ref(false);
const reapplicationMode = ref(false);
const agreementAccepted = ref(false);
const applicationFormRef = ref<ElFormInstance>();
const agreementVersion = '2026-07';
const privacyVersion = '2026-07';
const monthlyOrderOptions = ['1-100', '101-1,000', '1,001-10,000', '10,000+'];
const monthlyAmountOptions = [
  '0-10,000',
  '10,001-100,000',
  '100,001-1,000,000',
  '1,000,000+'
];

const defaultForm = (): MerchantApplicationEditorForm => ({
  merchantDisplayName: '',
  applicantName: '',
  phoneNumber: '',
  countryRegion: '中国',
  province: '',
  city: '',
  paymentUseCase: '',
  monthlyOrderRange: '1-100',
  monthlyAmountRange: '0-10,000',
  plannedPlatforms: ['WECHAT', 'ALIPAY'],
  agreementVersion,
  privacyVersion
});

const form = reactive<MerchantApplicationEditorForm>(defaultForm());
const rules = {
  merchantDisplayName: [
    { required: true, whitespace: true, message: '请输入商户展示名称', trigger: 'blur' },
    { max: 120, message: '商户展示名称不能超过 120 个字符', trigger: 'blur' }
  ],
  applicantName: [
    { required: true, whitespace: true, message: '请输入申请人称呼', trigger: 'blur' },
    { max: 80, message: '申请人称呼不能超过 80 个字符', trigger: 'blur' }
  ],
  phoneNumber: [
    { max: 32, message: '手机号不能超过 32 个字符', trigger: 'blur' }
  ],
  countryRegion: [
    { required: true, whitespace: true, message: '请输入国家或地区', trigger: 'blur' },
    { max: 80, message: '国家或地区不能超过 80 个字符', trigger: 'blur' }
  ],
  province: [{ max: 80, message: '省份不能超过 80 个字符', trigger: 'blur' }],
  city: [{ max: 80, message: '城市不能超过 80 个字符', trigger: 'blur' }],
  paymentUseCase: [
    { required: true, whitespace: true, message: '请描述收款使用场景', trigger: 'blur' },
    { max: 1000, message: '收款使用场景不能超过 1000 个字符', trigger: 'blur' }
  ],
  monthlyOrderRange: [
    { required: true, message: '请选择预计月订单量', trigger: 'change' },
    { max: 64, message: '预计月订单量不能超过 64 个字符', trigger: 'change' }
  ],
  monthlyAmountRange: [
    { required: true, message: '请选择预计月收款金额', trigger: 'change' },
    { max: 64, message: '预计月收款金额不能超过 64 个字符', trigger: 'change' }
  ],
  plannedPlatforms: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: '请至少选择一个收款平台',
      trigger: 'change'
    }
  ]
};

const viewState = computed(() => deriveOnboardingViewState(status.value));
const progress = computed(() => calculateOnboardingProgress(status.value));
const busy = computed(() => saving.value || submitting.value);
const reviewEnabled = computed(() => status.value?.reviewEnabled !== false);
const heroDescription = computed(() =>
  reviewEnabled.value
    ? '审核通过后完成二维码、设备配对和测试通知等必需任务，商户即可正式开通。'
    : '提交资料后将自动通过，随后完成二维码、设备配对和测试通知等必需任务即可正式开通。'
);
const applicationFormDescription = computed(() =>
  reviewEnabled.value
    ? '带 * 的资料需完整填写，保存草稿不会自动提交审核。'
    : '带 * 的资料需完整填写，保存草稿不会自动提交并创建商户。'
);
const agreementDescription = computed(() =>
  reviewEnabled.value
    ? '提交审核前需确认当前版本的服务协议与隐私政策。'
    : '提交并开通前需确认当前版本的服务协议与隐私政策。'
);
const submitButtonText = computed(() =>
  reviewEnabled.value ? '提交审核' : '提交并开通'
);
const showApplicationForm = computed(
  () => viewState.value.panel === 'APPLICATION_FORM' || reapplicationMode.value
);
const showReviewNote = computed(
  () =>
    Boolean(status.value?.application?.reviewNote) &&
    ['NEEDS_CHANGES', 'REJECTED'].includes(status.value?.application?.status || '')
);
const applicationFormTitle = computed(() =>
  status.value?.application?.status === 'NEEDS_CHANGES'
    ? '补充申请资料'
    : status.value?.application
      ? '编辑商户申请'
      : '填写商户申请'
);
const applicationStatusLabel = computed(() => {
  const applicationStatus = status.value?.application?.status;
  return applicationStatus
    ? applicationStatusLabels[applicationStatus]
    : '尚未申请';
});
const merchantLifecycleLabel = computed(() => {
  const lifecycle = status.value?.merchantLifecycle;
  return lifecycle ? merchantLifecycleLabels[lifecycle] || lifecycle : '尚未创建';
});
const applicationTagType = computed(() => {
  const applicationStatus = status.value?.application?.status;
  if (applicationStatus === 'APPROVED') return 'success';
  if (applicationStatus === 'REJECTED') return 'danger';
  if (applicationStatus === 'NEEDS_CHANGES') return 'warning';
  return applicationStatus ? 'primary' : 'info';
});
const lifecycleTagType = computed(() => {
  if (status.value?.merchantLifecycle === 'ACTIVE') return 'success';
  if (status.value?.merchantLifecycle === 'SUSPENDED') return 'danger';
  return status.value?.merchantLifecycle ? 'warning' : 'info';
});
const reviewPanelTitle = computed(() =>
  status.value?.application?.status === 'UNDER_REVIEW'
    ? '审核员正在审核资料'
    : '申请已提交，等待审核'
);
const reviewPanelDescription = computed(() =>
  status.value?.application?.status === 'UNDER_REVIEW'
    ? '审核期间申请资料暂不可修改，请留意审核结果。'
    : '平台尚未开始审核，如需修改资料可先撤回申请。'
);

const copyApplicationToForm = (application?: MerchantApplicationVO) => {
  Object.assign(form, defaultForm());
  if (!application) {
    agreementAccepted.value = false;
    return;
  }
  Object.assign(form, {
    merchantDisplayName: application.merchantDisplayName,
    applicantName: application.applicantName,
    phoneNumber: application.phoneNumber || '',
    countryRegion: application.countryRegion,
    province: application.province || '',
    city: application.city || '',
    paymentUseCase: application.paymentUseCase,
    monthlyOrderRange: application.monthlyOrderRange,
    monthlyAmountRange: application.monthlyAmountRange,
    plannedPlatforms: application.plannedPlatforms
      .split(',')
      .filter(
        (item): item is PlannedPaymentPlatform =>
          item === 'WECHAT' || item === 'ALIPAY'
      ),
    agreementVersion,
    privacyVersion
  });
  agreementAccepted.value =
    application.agreementVersion === agreementVersion &&
    application.privacyVersion === privacyVersion;
};

const load = async () => {
  loading.value = true;
    try {
      const response = await getMerchantOnboardingStatus();
      status.value = response.data;
      if (response.data?.onboardingAvailable === false) {
        ElMessage.info('平台账号无需申请商户，已返回首页');
        await router.replace('/index');
        return;
      }
      if (!reapplicationMode.value) {
      copyApplicationToForm(response.data?.application);
    }
  } finally {
    loading.value = false;
  }
};

const validateForm = async (requireAgreement: boolean) => {
  const valid = await applicationFormRef.value
    ?.validate()
    .then(() => true)
    .catch(() => false);
  if (!valid) return false;
  if (requireAgreement && !agreementAccepted.value) {
    ElMessage.warning('请先确认服务协议和隐私政策');
    return false;
  }
  return true;
};

const applicationPayload = (): MerchantApplicationSaveForm => ({
  merchantDisplayName: form.merchantDisplayName.trim(),
  applicantName: form.applicantName.trim(),
  phoneNumber: form.phoneNumber?.trim() || undefined,
  countryRegion: form.countryRegion.trim(),
  province: form.province?.trim() || undefined,
  city: form.city?.trim() || undefined,
  paymentUseCase: form.paymentUseCase.trim(),
  monthlyOrderRange: form.monthlyOrderRange.trim(),
  monthlyAmountRange: form.monthlyAmountRange.trim(),
  plannedPlatforms: form.plannedPlatforms.join(','),
  agreementVersion,
  privacyVersion
});

const persistApplication = async () => {
  const currentApplication = status.value?.application;
  const canUpdateCurrent =
    !reapplicationMode.value &&
    currentApplication &&
    ['DRAFT', 'NEEDS_CHANGES'].includes(currentApplication.status);
  const response = canUpdateCurrent
    ? await updateMerchantApplication(currentApplication.id, applicationPayload())
    : await createMerchantApplication(applicationPayload());

  if (!response.data) {
    throw new Error('商户申请保存响应为空');
  }
  status.value = {
    ...(status.value || {
      onboardingAvailable: true,
      reviewEnabled: true,
      verifiedEmail: '',
      mfaEnabled: false,
      checklist: []
    }),
    application: response.data
  };
  reapplicationMode.value = false;
  return response.data;
};

const saveDraft = async () => {
  if (busy.value || !(await validateForm(false))) return;
  saving.value = true;
  try {
    await persistApplication();
    ElMessage.success('草稿已保存');
  } finally {
    saving.value = false;
  }
};

const submitApplication = async () => {
  if (busy.value || !(await validateForm(true))) return;
  submitting.value = true;
  try {
    const application = await persistApplication();
    const response = await submitMerchantApplication(application.id);
    if (!response.data) {
      throw new Error('商户申请提交响应为空');
    }
    ElMessage.success(
      response.data.status === 'APPROVED'
        ? '申请已提交并自动通过'
        : '申请已提交审核'
    );
    await load();
  } finally {
    submitting.value = false;
  }
};

const withdrawApplication = async () => {
  const application = status.value?.application;
  if (!application || withdrawing.value) return;
  await ElMessageBox.confirm(
    '撤回后本次审核将结束，您可以稍后重新申请。是否继续？',
    '撤回商户申请',
    {
      type: 'warning',
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消'
    }
  );
  withdrawing.value = true;
  try {
    await withdrawMerchantApplication(application.id);
    ElMessage.success('申请已撤回');
    await load();
  } finally {
    withdrawing.value = false;
  }
};

const startReapplication = async () => {
  copyApplicationToForm(status.value?.application);
  agreementAccepted.value = false;
  reapplicationMode.value = true;
  await nextTick();
  document
    .querySelector('.onboarding-page .content-card')
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const cancelReapplication = () => {
  reapplicationMode.value = false;
  copyApplicationToForm(status.value?.application);
};

const checklistMeta = (code: string) =>
  ({
    OWNER_TOTP: {
      description: '建议绑定身份验证器并保存恢复码，为登录和敏感操作增加第二层保护。',
      action: '配置 MFA',
      path: '/user/profile?tab=security'
    },
    AGREEMENTS: {
      description: '确认当前服务协议与隐私政策版本。',
      action: '查看申请',
      path: PAYMENT_ROUTES.onboarding
    },
    QR_ASSET: {
      description: '添加至少一个微信或支付宝收款二维码。',
      action: '添加二维码',
      path: PAYMENT_ROUTES.qrcode
    },
    DEVICE_PAIRED: {
      description: '在 Android 真机输入配对码，将通知监听设备加入商户。',
      action: '配对设备',
      path: PAYMENT_ROUTES.device
    },
    DEVICE_ONLINE: {
      description: '保持通知监听、前台服务和心跳连接正常。',
      action: '检查设备',
      path: PAYMENT_ROUTES.device
    },
    TEST_NOTIFICATION: {
      description: '使用真实测试通知确认事件可以成功同步到服务端。',
      action: '查看支付事件',
      path: PAYMENT_ROUTES.event
    }
  })[code] || {
    description: '完成此项开通要求后刷新进度。',
    action: '去完成',
    path: undefined
  };

const navigateTo = async (path: string) => {
  try {
    await router.push(path);
  } catch {
    ElMessage.warning('目标页面暂时不可用，请从左侧菜单进入');
  }
};

onMounted(load);
</script>

<style scoped lang="scss">
.onboarding-page {
  --onboarding-border: color-mix(in srgb, var(--el-border-color) 72%, transparent);
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}

.hero-card,
.content-card {
  border-radius: 18px;
}

.hero-card {
  background:
    radial-gradient(circle at 85% 10%, rgb(64 112 255 / 14%), transparent 34%),
    var(--el-bg-color);
}

.hero-main,
.card-header,
.progress-row,
.task-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.eyebrow {
  margin: 0;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.hero-main h2 {
  margin: 8px 0;
  font-size: 26px;
}

.hero-description,
.card-header p,
.section-heading p,
.task-content p {
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.65;
}

.hero-meta {
  margin-top: 22px;
}

.progress-row {
  margin-top: 18px;

  > span {
    flex: 0 0 auto;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  :deep(.el-progress) {
    flex: 1;
  }
}

.review-alert {
  border-radius: 12px;
}

.card-header strong {
  font-size: 18px;
}

.card-header p {
  margin-top: 5px;
}

.form-section {
  padding: 22px;
  border: 1px solid var(--onboarding-border);
  border-radius: 14px;

  & + & {
    margin-top: 18px;
  }
}

.section-heading {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;

  h3 {
    margin: 0 0 3px;
    font-size: 16px;
  }
}

.section-index {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 700;
}

.platform-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;

  :deep(.el-checkbox) {
    margin-right: 0;
  }
}

.agreement-section {
  background: var(--el-fill-color-lighter);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 22px;
}

.result-card {
  :deep(.el-result) {
    padding-top: 22px;
  }
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.task-card {
  display: flex;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--onboarding-border);
  border-radius: 14px;
  background: var(--el-bg-color);

  &.completed {
    border-color: var(--el-color-success-light-5);
    background: var(--el-color-success-light-9);
  }
}

.task-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  place-items: center;
  border-radius: 12px;
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
  font-size: 20px;

  .completed & {
    background: var(--el-color-success-light-8);
    color: var(--el-color-success);
  }
}

.task-content {
  min-width: 0;
  flex: 1;

  p {
    min-height: 52px;
    margin: 8px 0 4px;
    font-size: 13px;
  }
}

@media (max-width: 768px) {
  .hero-main,
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-meta {
    :deep(.el-descriptions__body) {
      overflow-x: auto;
    }
  }

  .task-grid {
    grid-template-columns: 1fr;
  }

  .form-section {
    padding: 16px;
  }

  .form-actions {
    flex-wrap: wrap;

    .el-button {
      flex: 1;
    }
  }
}
</style>
