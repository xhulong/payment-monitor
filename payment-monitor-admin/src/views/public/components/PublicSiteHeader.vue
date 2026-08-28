<template>
  <header class="site-header" :class="{ 'is-scrolled': scrolled }">
    <div class="site-container header-inner">
      <router-link class="site-brand" to="/" aria-label="返回 LuLuPay 官网首页" @click="closeMenu">
        <img :src="logo" alt="LuLuPay Logo" />
        <span>
          <strong>LuLuPay</strong>
          <small>码支付</small>
        </span>
      </router-link>

      <button
        type="button"
        class="mobile-menu-button"
        :aria-expanded="mobileOpen"
        aria-label="切换网站导航"
        @click="mobileOpen = !mobileOpen"
      >
        <span></span>
        <span></span>
        <span></span>
      </button>

      <nav class="site-nav" :class="{ 'is-open': mobileOpen }" aria-label="官网导航">
        <button
          v-for="item in navigation"
          :key="item.id"
          type="button"
          class="site-nav-link"
          @click="navigateToSection(item.id)"
        >
          {{ item.label }}
        </button>
        <router-link class="site-nav-link guide-link" to="/guide" @click="closeMenu">
          使用教程
        </router-link>
        <div class="header-actions">
          <router-link class="button button-ghost button-compact" :to="hasSession ? '/index' : '/login'" @click="closeMenu">
            {{ hasSession ? '进入控制台' : '登录' }}
          </router-link>
          <router-link v-if="!hasSession" class="button button-primary button-compact" to="/register" @click="closeMenu">
            免费注册
          </router-link>
        </div>
      </nav>
    </div>
  </header>
</template>

<script setup lang="ts">
import { getToken } from '@/utils/auth';
import logo from '@/assets/logo/logo.png';

const router = useRouter();
const route = useRoute();
const mobileOpen = ref(false);
const scrolled = ref(false);
const hasSession = computed(() => Boolean(getToken()));

const navigation = [
  { id: 'workflow', label: '工作原理' },
  { id: 'features', label: '核心功能' },
  { id: 'download', label: '下载监控端' }
] as const;

const closeMenu = () => {
  mobileOpen.value = false;
};

const navigateToSection = async (id: string) => {
  closeMenu();
  const hash = `#${id}`;
  if (route.path === '/' || route.path === '/overview') {
    await router.replace({ path: route.path, hash });
    document.querySelector(hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return;
  }
  await router.push({ path: '/', hash });
};

const updateScrollState = () => {
  scrolled.value = window.scrollY > 18;
};

onMounted(() => {
  updateScrollState();
  window.addEventListener('scroll', updateScrollState, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateScrollState);
});
</script>
