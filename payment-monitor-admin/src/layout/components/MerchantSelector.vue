<template>
  <div class="merchant-selector">
    <el-select
      v-if="store.canAccessAllMerchants"
      v-model="selected"
      :loading="store.loading || store.optionsLoading"
      filterable
      remote
      reserve-keyword
      size="small"
      placeholder="全部商户"
      style="width: 220px"
      :remote-method="remoteSearch"
      @visible-change="handleVisibleChange"
    >
      <el-option label="全部商户" :value="ALL_MERCHANTS_VALUE">
        <div class="merchant-option merchant-option--all">
          <strong>全部商户</strong>
          <span>平台数据范围</span>
        </div>
      </el-option>
      <el-option
        v-for="merchant in store.merchants"
        :key="String(merchant.id)"
        :label="`${merchant.name} (${merchant.merchantCode})`"
        :value="String(merchant.id)"
      >
        <div class="merchant-option">
          <strong>{{ merchant.name }}</strong>
          <span>
            {{ merchant.merchantCode }}
            ·
            {{ merchant.lifecycleStatus || (merchant.status === '0' ? 'ACTIVE' : 'DISABLED') }}
          </span>
        </div>
      </el-option>
    </el-select>
    <el-tag v-else-if="store.context?.merchantName" type="info" effect="plain">
      {{ store.context.merchantName }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { useMerchantStore } from '@/store/modules/merchant';

const ALL_MERCHANTS_VALUE = '__ALL_MERCHANTS__';
const store = useMerchantStore();
const selected = computed({
  get: () => store.selectedMerchantId || ALL_MERCHANTS_VALUE,
  set: value => {
    store.select(value === ALL_MERCHANTS_VALUE ? undefined : value);
  }
});

const remoteSearch = (keyword: string) => {
  store.searchOptions(keyword).catch(() => undefined);
};

const handleVisibleChange = (visible: boolean) => {
  if (visible && store.merchants.length === 0) {
    remoteSearch('');
  }
};

onMounted(() => {
  if (!store.context) {
    store.load().catch(() => undefined);
  }
});
</script>

<style scoped>
.merchant-selector {
  display: inline-flex;
  align-items: center;
  margin-right: 6px;
}

.merchant-option {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  line-height: 1.25;
}

.merchant-option strong {
  overflow: hidden;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
}

.merchant-option span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.merchant-option--all strong {
  color: var(--el-color-primary);
}
</style>
