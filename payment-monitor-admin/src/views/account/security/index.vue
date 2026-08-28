<template>
  <div class="security-setup-page">
    <div class="security-setup-shell">
      <mfa-security-panel />
    </div>
  </div>
</template>

<script setup lang="ts">
import MfaSecurityPanel from './mfa-security-panel.vue';
</script>

<style scoped lang="scss">
.security-setup-page {
  box-sizing: border-box;
  min-height: 100vh;
  padding: 24px;
  background: var(--el-bg-color-page);
}

.security-setup-shell {
  width: min(1080px, 100%);
  margin: 0 auto;
}

@media (max-width: 640px) {
  .security-setup-page {
    padding: 12px;
  }
}
</style>
