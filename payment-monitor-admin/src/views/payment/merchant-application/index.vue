<template>
  <div class="p-2 merchant-application-page">
    <el-card v-loading="settingsLoading" shadow="never" class="review-mode-card">
      <div class="review-mode-content">
        <div>
          <div class="review-mode-title">
            <h3>商户入驻审核模式</h3>
            <el-tag :type="reviewSettings.reviewEnabled ? 'primary' : 'success'">
              {{ reviewSettings.reviewEnabled ? '人工审核' : '自动通过' }}
            </el-tag>
          </div>
          <p>
            {{
              reviewSettings.reviewEnabled
                ? '新提交的申请进入待审核队列，由审核员认领并处理。'
                : '新提交的申请将自动创建商户并进入开通清单；现有待审核申请保持不变。'
            }}
          </p>
        </div>
        <div class="review-mode-action">
          <el-switch
            v-model="reviewSettings.reviewEnabled"
            :disabled="!settingsLoaded || !canEditReviewSettings || settingsSaving"
            :loading="settingsSaving"
            active-text="开启审核"
            inactive-text="关闭审核"
            @change="changeReviewSettings"
          />
          <span v-if="!canEditReviewSettings" class="permission-tip">仅超级管理员可修改</span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 180px">
            <el-option v-for="item in statuses" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form>
    </el-card>
    <el-card shadow="never" class="mt-3">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="merchantDisplayName" label="商户名称" min-width="180" />
        <el-table-column prop="verifiedEmail" label="邮箱" min-width="220" />
        <el-table-column prop="applicantName" label="申请人" width="120" />
        <el-table-column prop="status" label="状态" width="140" />
        <el-table-column prop="submittedAt" label="提交时间" min-width="180" />
        <el-table-column label="操作" fixed="right" width="260">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row as MerchantApplicationVO)">查看</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" link type="warning" @click="claim(row as MerchantApplicationVO)">认领</el-button>
            <el-button v-if="row.status === 'UNDER_REVIEW'" link type="success" @click="review(row as MerchantApplicationVO, 'approve')">通过</el-button>
            <el-button v-if="row.status === 'UNDER_REVIEW'" link type="warning" @click="review(row as MerchantApplicationVO, 'changes')">补充</el-button>
            <el-button v-if="row.status === 'UNDER_REVIEW'" link type="danger" @click="review(row as MerchantApplicationVO, 'reject')">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load" />
    </el-card>
    <el-drawer v-model="drawer.visible" title="申请详情" size="560px">
      <el-descriptions v-if="drawer.row" :column="1" border>
        <el-descriptions-item label="商户名称">{{ drawer.row.merchantDisplayName }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ drawer.row.applicantName }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ drawer.row.verifiedEmail }}</el-descriptions-item>
        <el-descriptions-item label="使用场景">{{ drawer.row.paymentUseCase }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ drawer.row.plannedPlatforms }}</el-descriptions-item>
        <el-descriptions-item label="审核意见">{{ drawer.row.reviewNote || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import {
  approveMerchantApplication,
  claimMerchantApplication,
  getMerchantApplicationReviewSettings,
  listMerchantApplications,
  rejectMerchantApplication,
  requestMerchantApplicationChanges,
  updateMerchantApplicationReviewSettings
} from '@/api/payment';
import type { MerchantApplicationVO } from '@/api/payment/types';
import { checkPermi } from '@/utils/permission';
import { requestPaymentStepUp } from '@/utils/payment-step-up';

const loading = ref(false);
const settingsLoading = ref(false);
const settingsLoaded = ref(false);
const settingsSaving = ref(false);
const reviewSettings = reactive({ reviewEnabled: true });
const rows = ref<MerchantApplicationVO[]>([]);
const total = ref(0);
const query = reactive({ pageNum: 1, pageSize: 20, status: '' });
const statuses = ['SUBMITTED', 'UNDER_REVIEW', 'NEEDS_CHANGES', 'APPROVED', 'REJECTED'];
const drawer = reactive({ visible: false, row: undefined as MerchantApplicationVO | undefined });
const canEditReviewSettings = computed(() =>
  checkPermi(['payment:merchant-application:settings'])
);

const loadReviewSettings = async () => {
  settingsLoading.value = true;
  settingsLoaded.value = false;
  try {
    const response = await getMerchantApplicationReviewSettings();
    reviewSettings.reviewEnabled = response.data?.reviewEnabled !== false;
    settingsLoaded.value = true;
  } finally {
    settingsLoading.value = false;
  }
};

const changeReviewSettings = async (value: string | number | boolean) => {
  const reviewEnabled = Boolean(value);
  const previous = !reviewEnabled;
  try {
    await ElMessageBox.confirm(
      reviewEnabled
        ? '开启后，新提交的商户申请将恢复人工审核。是否继续？'
        : '关闭后，新提交的申请将自动通过并创建商户，现有待审核或审核中的申请不受影响。是否继续？',
      reviewEnabled ? '开启商户入驻审核' : '关闭商户入驻审核',
      {
        type: reviewEnabled ? 'info' : 'warning',
        confirmButtonText: '确认修改',
        cancelButtonText: '取消'
      }
    );
    settingsSaving.value = true;
    const token = await requestPaymentStepUp(
      'MERCHANT_APPLICATION_REVIEW_SETTINGS',
      '修改商户入驻审核设置'
    );
    const response = await updateMerchantApplicationReviewSettings(
      { reviewEnabled },
      token
    );
    reviewSettings.reviewEnabled = response.data?.reviewEnabled ?? reviewEnabled;
    ElMessage.success(
      reviewSettings.reviewEnabled ? '已开启商户入驻人工审核' : '已关闭商户入驻人工审核'
    );
  } catch {
    reviewSettings.reviewEnabled = previous;
  } finally {
    settingsSaving.value = false;
  }
};

const load = async () => {
  loading.value = true;
  try {
    const response = await listMerchantApplications(query);
    rows.value = response.data?.rows || [];
    total.value = response.data?.total || 0;
  } finally {
    loading.value = false;
  }
};
const open = (row: MerchantApplicationVO) => {
  drawer.row = row;
  drawer.visible = true;
};
const claim = async (row: MerchantApplicationVO) => {
  await claimMerchantApplication(row.id);
  ElMessage.success('申请已认领');
  await load();
};
const review = async (row: MerchantApplicationVO, action: string) => {
  const note = await ElMessageBox.prompt('请输入审核意见', '审核操作', { inputType: 'textarea' });
  const value = note.value;
  const token = await requestPaymentStepUp('MERCHANT_APPLICATION_REVIEW', '审核商户申请');
  if (action === 'approve') await approveMerchantApplication(row.id, value, token);
  if (action === 'changes') await requestMerchantApplicationChanges(row.id, value, token);
  if (action === 'reject') await rejectMerchantApplication(row.id, value, token);
  ElMessage.success('操作完成');
  await load();
};
onMounted(() => {
  void load();
  void loadReviewSettings();
});
</script>

<style scoped lang="scss">
.merchant-application-page {
  .review-mode-card {
    margin-bottom: 12px;
  }
}

.review-mode-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    line-height: 1.6;
  }
}

.review-mode-title {
  display: flex;
  align-items: center;
  gap: 10px;

  h3 {
    margin: 0;
    font-size: 17px;
  }
}

.review-mode-action {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.permission-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (width <= 768px) {
  .review-mode-content {
    align-items: flex-start;
    flex-direction: column;
  }

  .review-mode-action {
    align-items: flex-start;
  }
}
</style>
