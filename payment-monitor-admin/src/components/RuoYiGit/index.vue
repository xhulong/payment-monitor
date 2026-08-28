<template>
  <div>
    <svg-icon icon-class="github" @click="goto" />
  </div>
</template>

<script setup lang="ts">
const url = ref('https://github.com/xhulong/payment-monitor-admin');

function goto() {
  window.open(url.value);
}
</script>
