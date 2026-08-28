<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="page-heading">
          <div>
            <h2>发送记录</h2>
            <p>仅展示脱敏收件人、主题和投递状态，不展示验证码、Token 或邮件正文。</p>
          </div>
          <el-button icon="Refresh" @click="load">刷新</el-button>
        </div>
      </template>

      <el-form :model="query" inline class="query-form">
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="query.messageType" clearable filterable placeholder="全部类型" style="width: 240px">
            <el-option v-for="item in messageTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="createdRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="search">查询</el-button>
          <el-button icon="RefreshLeft" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="消息类型" min-width="180">
          <template #default="{ row }">{{ messageTypeLabel(row.messageType) }}</template>
        </el-table-column>
        <el-table-column prop="maskedRecipient" label="收件人" min-width="190" />
        <el-table-column prop="subject" label="主题" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尝试次数" width="105" align="center">
          <template #default="{ row }">{{ row.attemptCount }} / {{ row.maxAttempts }}</template>
        </el-table-column>
        <el-table-column label="发送时间" width="180">
          <template #default="{ row }">{{ formatTime(row.sentAt) }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.lastError || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.retryable"
              v-hasPermi="['system:mail-outbox:retry']"
              link
              type="warning"
              @click="retry(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="load"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="邮件发送详情" width="680px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="消息类型">{{ messageTypeLabel(detail.messageType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="脱敏收件人">{{ detail.maskedRecipient }}</el-descriptions-item>
        <el-descriptions-item label="尝试次数">{{ detail.attemptCount }} / {{ detail.maxAttempts }}</el-descriptions-item>
        <el-descriptions-item label="主题" :span="2">{{ detail.subject }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ formatTime(detail.sentAt) }}</el-descriptions-item>
        <el-descriptions-item label="下次重试">{{ formatTime(detail.nextAttemptAt) }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ formatTime(detail.expiresAt) }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ detail.lastError || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  getMailOutbox,
  listMailOutbox,
  retryMailOutbox
} from '@/api/system/mail';
import type {
  MailOutboxQuery,
  MailOutboxVO
} from '@/api/system/mail/types';
import { formatApiTime } from '@/api/payment/time';
import { requestPaymentStepUp } from '@/utils/payment-step-up';

const loading = ref(false);
const rows = ref<MailOutboxVO[]>([]);
const total = ref(0);
const detail = ref<MailOutboxVO>();
const detailVisible = ref(false);
const createdRange = ref<[string, string]>();
const query = reactive<MailOutboxQuery>({
  pageNum: 1,
  pageSize: 10,
  status: '',
  messageType: ''
});

const statusOptions = [
  { value: 'PENDING', label: '待发送' },
  { value: 'RETRYING', label: '重试中' },
  { value: 'SENT', label: '已发送' },
  { value: 'DEAD', label: '发送失败' },
  { value: 'CANCELLED', label: '已取消' }
];

const messageTypeOptions = [
  ['MERCHANT_SIGNUP_CODE', '注册验证码'],
  ['PASSWORD_RESET_CODE', '密码重置验证码'],
  ['EMAIL_CHANGE_CODE', '新邮箱验证码'],
  ['EMAIL_CHANGED_NOTICE', '旧邮箱变更通知'],
  ['EMAIL_CHANGED_CONFIRMATION', '新邮箱生效通知'],
  ['MERCHANT_INVITATION', '商户成员邀请'],
  ['ACCOUNT_REGISTERED', '账号注册成功'],
  ['PASSWORD_CHANGED_NOTICE', '密码修改成功'],
  ['PASSWORD_RESET_SUCCESS', '密码重置成功'],
  ['MFA_ENABLED_NOTICE', 'MFA 首次启用'],
  ['MFA_REPLACED_NOTICE', 'MFA 重新配置'],
  ['MFA_RECOVERY_CODES_REGENERATED_NOTICE', '恢复码重新生成'],
  ['MERCHANT_APPLICATION_SUBMITTED', '商户申请已提交'],
  ['MERCHANT_APPLICATION_REVIEW_NOTICE', '新商户申请待审核'],
  ['MERCHANT_APPLICATION_NEEDS_CHANGES', '商户申请需补资料'],
  ['MERCHANT_APPLICATION_APPROVED', '商户申请审核通过'],
  ['MERCHANT_APPLICATION_REJECTED', '商户申请被拒绝']
].map(([value, label]) => ({ value, label }));

const messageTypeLabel = (value: string) =>
  messageTypeOptions.find(item => item.value === value)?.label || value;
const statusLabel = (value: string) =>
  statusOptions.find(item => item.value === value)?.label || value;
const statusType = (value: string) => {
  if (value === 'SENT') return 'success';
  if (value === 'DEAD') return 'danger';
  if (value === 'RETRYING') return 'warning';
  return 'info';
};
const formatTime = (value?: string) => formatApiTime(value);

const load = async () => {
  loading.value = true;
  try {
    const response = await listMailOutbox({
      ...query,
      startTime: createdRange.value?.[0],
      endTime: createdRange.value?.[1]
    });
    rows.value = response.data.rows;
    total.value = response.data.total;
  } finally {
    loading.value = false;
  }
};

const search = () => {
  query.pageNum = 1;
  load();
};

const resetQuery = () => {
  query.status = '';
  query.messageType = '';
  createdRange.value = undefined;
  search();
};

const showDetail = async (id: string | number) => {
  const response = await getMailOutbox(id);
  detail.value = response.data;
  detailVisible.value = true;
};

const retry = async (row: MailOutboxVO | Record<string, any>) => {
  await ElMessageBox.confirm(
    '仅发送失败且仍在有效期内的邮件可以重试，是否继续？',
    '重试邮件',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp('MAIL_OUTBOX_RETRY', '重试发送邮件');
  await retryMailOutbox(row.id, token);
  ElMessage.success('邮件已重新进入发送队列');
  await load();
};

onMounted(load);
</script>

<style scoped lang="scss">
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.page-heading h2 {
  margin: 0;
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.query-form {
  margin-bottom: 6px;
}
</style>
