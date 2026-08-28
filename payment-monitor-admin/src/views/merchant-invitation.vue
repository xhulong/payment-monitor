<template>
  <div class="invitation-page">
    <el-card class="invitation-card" shadow="never">
      <div class="eyebrow">MERCHANT TEAM</div>
      <h1>接受商户成员邀请</h1>
      <p>请使用与邀请邮箱一致的账号登录。接受后，该账号将绑定到邀请所属商户。</p>
      <el-alert
        v-if="message"
        :type="accepted ? 'success' : 'warning'"
        show-icon
        :closable="false"
        :title="message"
      />
      <template v-if="!loggedIn">
        <el-button type="primary" @click="goLogin">登录并接受邀请</el-button>
        <el-button @click="goRegister">没有账号，先注册</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :loading="loading" :disabled="accepted" @click="accept">
          {{ accepted ? '邀请已接受' : '确认接受邀请' }}
        </el-button>
        <el-button @click="router.push('/')">返回管理后台</el-button>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { acceptMerchantInvitation } from '@/api/payment';
import { getToken } from '@/utils/auth';
import { merchantRoleLabel } from '@/utils/merchant-role';

const route = useRoute();
const router = useRouter();
const token = computed(() => String(route.params.token || ''));
const loggedIn = computed(() => Boolean(getToken()));
const loading = ref(false);
const accepted = ref(false);
const message = ref('');

const accept = async () => {
  if (!token.value) return;
  loading.value = true;
  try {
    const result = (await acceptMerchantInvitation(token.value)).data;
    accepted.value = true;
    message.value = `邀请已接受，当前岗位：${merchantRoleLabel(result?.roleCode)}`;
    ElMessage.success('已加入商户，请重新登录以刷新岗位权限');
  } finally {
    loading.value = false;
  }
};

const goLogin = () => {
  router.push({ path: '/login', query: { redirect: route.fullPath } });
};

const goRegister = () => {
  router.push({ path: '/register', query: { invitationToken: token.value } });
};
</script>

<style scoped lang="scss">
.invitation-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 15% 10%, rgba(66, 104, 255, 0.3), transparent 32%),
    radial-gradient(circle at 85% 90%, rgba(36, 202, 191, 0.2), transparent 34%),
    #08111f;
}
.invitation-card {
  width: min(560px, 100%);
  padding: 18px;
  border-radius: 24px;
}
.eyebrow {
  color: #4268ff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
}
h1 {
  margin: 12px 0;
}
p {
  margin-bottom: 22px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
.el-alert {
  margin-bottom: 18px;
}
</style>
