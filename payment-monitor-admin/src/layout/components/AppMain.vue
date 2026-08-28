<template>
  <section class="app-main">
    <div
      v-if="showMobileMerchantScope"
      class="mobile-merchant-scope"
    >
      <span>商户范围</span>
      <merchant-selector />
    </div>
    <router-view v-slot="{ Component, route }">
      <transition :enter-active-class="animate" mode="out-in">
        <keep-alive :include="tagsViewStore.cachedViews">
          <component :is="Component" v-if="!route.meta.link" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>
    <iframe-toggle />
  </section>
</template>

<script setup name="AppMain" lang="ts">
import animateConfig from '@/animate';
import { useFullHeightTable } from '@/hooks/table/useFullHeightTable';
import { useSettingsStore } from '@/store/modules/settings';
import { useMerchantStore } from '@/store/modules/merchant';
import { useTagsViewStore } from '@/store/modules/tagsView';
import MerchantSelector from './MerchantSelector.vue';
import IframeToggle from './IframeToggle/index.vue';

const route = useRoute();
const tagsViewStore = useTagsViewStore();
const merchantStore = useMerchantStore();
const showMobileMerchantScope = computed(
  () =>
    route.path.startsWith('/payment/') &&
    merchantStore.canAccessAllMerchants
);
useFullHeightTable();

// 随机动画集合
const animate = ref<string>('');
watch(
  () => useSettingsStore().animationEnable,
  (val: boolean) => {
    if (val) {
      animate.value = animateConfig.animateList[Math.floor(Math.random() * animateConfig.animateList.length)] as string;
    } else {
      animate.value = animateConfig.defaultAnimate as string;
    }
  },
  { immediate: true }
);

watchEffect(() => {
  addIframe();
});

function addIframe() {
  if (route.meta.link) {
    useTagsViewStore().addIframeView(route);
  }
}
</script>

<style lang="scss" scoped>
.app-main {
  min-height: 100vh;
  width: 100%;
  position: relative;
  overflow: hidden;
  padding: 12px;

  &:fullscreen,
  &:-webkit-full-screen,
  &:-moz-full-screen,
  &:-ms-fullscreen {
    background: var(--el-bg-color);
    overflow-y: auto;
  }
}

.app-main:not(.with-fixed-header) {
  min-height: calc(100vh - 64px);
}

.app-main.with-tags-view:not(.with-fixed-header) {
  min-height: calc(100vh - 105px);
}

.app-main.with-fixed-header {
  padding-top: 76px;
  min-height: calc(100vh - 76px);
}

.app-main.with-fixed-header.with-tags-view {
  min-height: calc(100vh - 111px);
  padding-top: 111px;
}

.mobile-merchant-scope {
  display: none;
}

@media (max-width: 768px) {
  .mobile-merchant-scope {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    padding: 10px 12px;
    border: 1px solid var(--app-surface-border);
    border-radius: 14px;
    background: var(--app-surface-bg);
  }

  .mobile-merchant-scope > span {
    flex-shrink: 0;
    color: var(--app-text-muted);
    font-size: 13px;
  }

  .mobile-merchant-scope :deep(.merchant-selector) {
    min-width: 0;
    flex: 1;
    justify-content: flex-end;
    margin-right: 0;
  }

  .mobile-merchant-scope :deep(.el-select) {
    width: min(220px, 100%) !important;
  }
}
</style>
<style lang="scss">
// fix css style bug in open el-dialog
.el-popup-parent--hidden {
  .fixed-header {
    padding-right: 6px;
  }
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background-color: var(--el-fill-color-lighter);
}

::-webkit-scrollbar-thumb {
  background-color: var(--el-text-color-placeholder);
  border-radius: 999px;
}
</style>
