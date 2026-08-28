<template>
  <el-select
    :model-value="normalizedValue"
    :disabled="disabled || !store.canAccessAllMerchants"
    :loading="store.optionsLoading"
    filterable
    remote
    reserve-keyword
    clearable
    :placeholder="placeholder"
    :remote-method="remoteSearch"
    style="width: 100%"
    @update:model-value="handleChange"
    @visible-change="handleVisibleChange"
  >
    <el-option
      v-for="merchant in availableMerchants"
      :key="String(merchant.id)"
      :label="`${merchant.name} (${merchant.merchantCode})`"
      :value="String(merchant.id)"
      :disabled="activeOnly && merchant.lifecycleStatus !== 'ACTIVE'"
    >
      <div class="merchant-target-option">
        <strong>{{ merchant.name }}</strong>
        <span>
          {{ merchant.merchantCode }}
          ·
          {{ merchant.lifecycleStatus || (merchant.status === '0' ? 'ACTIVE' : 'DISABLED') }}
        </span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { useMerchantStore } from '@/store/modules/merchant';

const props = withDefaults(
  defineProps<{
    modelValue?: string | number;
    disabled?: boolean;
    activeOnly?: boolean;
    placeholder?: string;
  }>(),
  {
    disabled: false,
    activeOnly: false,
    placeholder: '请选择目标商户'
  }
);
const emit = defineEmits<{
  'update:modelValue': [value: string | undefined];
}>();
const store = useMerchantStore();
const normalizedValue = computed(() =>
  props.modelValue == null || String(props.modelValue).trim() === ''
    ? undefined
    : String(props.modelValue)
);
const availableMerchants = computed(() =>
  props.activeOnly
    ? store.merchants.filter(
        merchant =>
          merchant.lifecycleStatus === 'ACTIVE' ||
          String(merchant.id) === normalizedValue.value
      )
    : store.merchants
);

const remoteSearch = (keyword: string) => {
  store
    .searchOptions(keyword, props.activeOnly ? '0' : undefined)
    .catch(() => undefined);
};
const handleVisibleChange = (visible: boolean) => {
  if (visible && store.merchants.length === 0) remoteSearch('');
};
const handleChange = (value?: string) => {
  emit('update:modelValue', value || undefined);
};
</script>

<style scoped>
.merchant-target-option {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.merchant-target-option span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
</style>
