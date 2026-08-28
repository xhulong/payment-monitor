<template>
  <div class="p-2">
    <el-card shadow="never">
      <div class="toolbar">
        <div><h3>商户成员</h3><p>一个普通账号只能属于一个商户，所有者转让需要单独邀请确认。</p></div>
        <div class="toolbar-actions">
          <el-button type="primary" @click="inviteVisible = true">邀请成员</el-button>
          <el-button type="success" plain :disabled="multiple || mixedMerchantSelection" @click="batchStatus('0')">批量启用</el-button>
          <el-button type="warning" plain :disabled="multiple || mixedMerchantSelection" @click="batchStatus('1')">批量停用</el-button>
          <el-button type="danger" plain :disabled="multiple || mixedMerchantSelection" @click="batchRemove">批量移除</el-button>
        </div>
      </div>
    </el-card>
    <el-card shadow="never" class="mt-3">
      <el-table v-loading="loading" :data="rows" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" :selectable="row => row.roleCode !== 'OWNER'" />
        <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
          <template #default="{ row }">
            <strong>{{ row.merchantName || '-' }}</strong>
            <div class="table-subtitle">{{ row.merchantCode || row.merchantId }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" min-width="220" />
        <el-table-column label="岗位" width="150">
          <template #default="{ row }">
            <el-select
              v-model="row.roleCode"
              size="small"
              :disabled="row.roleCode === 'OWNER'"
              @change="changeRole(row as MerchantMemberVO)"
            >
              <el-option
                v-for="role in merchantRoleOptions"
                :key="role.value"
                :label="role.label"
                :value="role.value"
                :disabled="role.value === 'OWNER'"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="mfaEnabled" label="MFA" width="100">
          <template #default="{ row }"><el-tag :type="row.mfaEnabled ? 'success' : 'warning'">{{ row.mfaEnabled ? '已启用' : '未启用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button link type="danger" @click="remove(row as MerchantMemberVO)">移除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="inviteVisible" title="邀请成员" width="460px">
      <el-form :model="invite">
        <el-form-item v-if="showMerchantColumn" label="目标商户" required>
          <payment-merchant-target-select v-model="invite.merchantId" active-only />
        </el-form-item>
        <el-form-item label="邮箱"><el-input v-model="invite.email" /></el-form-item>
        <el-form-item label="岗位">
          <el-select v-model="invite.roleCode">
            <el-option
              v-for="role in merchantRoleOptions"
              :key="role.value"
              :label="role.label"
              :value="role.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="inviteVisible = false">取消</el-button><el-button type="primary" @click="submitInvite">发送邀请</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {
  batchRemoveMerchantMembers,
  batchUpdateMerchantMemberStatus,
  createMerchantInvitation,
  listMerchantMembers,
  removeMerchantMember,
  updateMerchantMember
} from '@/api/payment';
import type { MerchantMemberVO } from '@/api/payment/types';
import PaymentMerchantTargetSelect from '@/components/PaymentMerchantTargetSelect/index.vue';
import {
  hasMixedMerchantSelection,
  usePaymentMerchantScope
} from '@/hooks/payment/useMerchantScope';
import { useTableSelection } from '@/hooks/table/useTableSelection';
import { merchantRoleOptions } from '@/utils/merchant-role';
import { requestPaymentStepUp } from '@/utils/payment-step-up';

const loading = ref(false);
const rows = ref<MerchantMemberVO[]>([]);
const { ids, selectedRows, multiple, handleSelectionChange, clearSelection } =
  useTableSelection<MerchantMemberVO>(item => item.userId);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();
const mixedMerchantSelection = computed(() =>
  hasMixedMerchantSelection(selectedRows.value)
);
const inviteVisible = ref(false);
const invite = reactive<{
  merchantId?: string;
  email: string;
  roleCode: MerchantMemberVO['roleCode'];
}>({ merchantId: undefined, email: '', roleCode: 'VIEWER' });
const load = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  loading.value = true;
  try {
    const response = await listMerchantMembers();
    if (scopeVersion !== merchantStore.scopeVersion) return;
    rows.value = response.data || [];
  } finally { loading.value = false; }
};
const submitInvite = async () => {
  if (!invite.merchantId) {
    ElMessage.warning('请选择目标商户');
    return;
  }
  const token = await requestPaymentStepUp('MERCHANT_MEMBER_MANAGE', '邀请商户成员');
  const response = await createMerchantInvitation(invite, token);
  inviteVisible.value = false;
  ElMessage.success(response.data?.acceptanceToken ? `邀请已创建，令牌：${response.data.acceptanceToken}` : '邀请已发送');
};
const changeRole = async (row: MerchantMemberVO) => {
  const token = await requestPaymentStepUp('MERCHANT_MEMBER_MANAGE', '修改成员岗位');
  await updateMerchantMember(row.userId, { roleCode: row.roleCode }, token);
  ElMessage.success('岗位已更新');
};
const remove = async (row: MerchantMemberVO) => {
  await ElMessageBox.confirm(
    `确定从商户“${row.merchantName || row.merchantId}”移除 ${row.email || row.nickname || row.userId}？`,
    '确认'
  );
  const token = await requestPaymentStepUp('MERCHANT_MEMBER_MANAGE', '移除商户成员');
  await removeMerchantMember(row.userId, token);
  await load();
};
const batchStatus = async (status: '0' | '1') => {
  if (mixedMerchantSelection.value) {
    ElMessage.warning('同一次批量操作只能处理一个商户');
    return;
  }
  const action = status === '0' ? '启用' : '停用';
  await ElMessageBox.confirm(`确认${action}选中的 ${ids.value.length} 名成员吗？`, '确认');
  const token = await requestPaymentStepUp('MERCHANT_MEMBER_MANAGE', `批量${action}商户成员`);
  await batchUpdateMerchantMemberStatus(ids.value, status, token);
  ElMessage.success(`批量${action}成功`);
  await load();
};
const batchRemove = async () => {
  if (mixedMerchantSelection.value) {
    ElMessage.warning('同一次批量操作只能处理一个商户');
    return;
  }
  await ElMessageBox.confirm(`确认移除选中的 ${ids.value.length} 名成员吗？`, '确认');
  const token = await requestPaymentStepUp('MERCHANT_MEMBER_MANAGE', '批量移除商户成员');
  await batchRemoveMerchantMembers(ids.value, token);
  ElMessage.success('批量移除成功');
  await load();
};
watch(inviteVisible, visible => {
  if (visible) invite.merchantId = defaultTargetMerchantId();
});
watchScope(async () => {
  inviteVisible.value = false;
  clearSelection();
  await load();
});
onMounted(async () => {
  if (!merchantStore.context) await merchantStore.load();
  await load();
});
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; }
.toolbar h3 { margin:0 0 6px; }
.toolbar p { margin:0; color:#7d899c; }
.toolbar-actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:8px; }
.table-subtitle { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
