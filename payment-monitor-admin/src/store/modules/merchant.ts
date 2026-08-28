import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import {
  getMerchant,
  getMerchantContext,
  listMerchantOptions
} from '@/api/payment';
import type { MerchantContextVO, MerchantVO } from '@/api/payment/types';
import {
  getSelectedMerchantId,
  setSelectedMerchantId
} from '@/utils/merchant';

const SCOPE_STORAGE_VERSION_KEY = 'payment-merchant-scope-storage-version';
const SCOPE_STORAGE_VERSION = '2';

export const useMerchantStore = defineStore('payment-merchant', () => {
  const context = ref<MerchantContextVO>();
  const merchants = ref<MerchantVO[]>([]);
  const loading = ref(false);
  const optionsLoading = ref(false);
  const selectedMerchantId = ref<string | undefined>();
  const scopeVersion = ref(0);
  let optionRequestVersion = 0;

  const canAccessAllMerchants = computed(
    () => context.value?.canAccessAllMerchants === true
  );
  const isAllMerchants = computed(
    () => canAccessAllMerchants.value && !selectedMerchantId.value
  );
  const selectedMerchant = computed(() =>
    merchants.value.find(
      item => String(item.id) === selectedMerchantId.value
    )
  );

  const mergeOptions = (rows: MerchantVO[]) => {
    const merged = new Map<string, MerchantVO>();
    for (const merchant of [...merchants.value, ...rows]) {
      merged.set(String(merchant.id), merchant);
    }
    merchants.value = [...merged.values()];
  };

  const searchOptions = async (
    keyword = '',
    status?: string
  ): Promise<MerchantVO[]> => {
    if (!canAccessAllMerchants.value) return merchants.value;
    const requestVersion = ++optionRequestVersion;
    optionsLoading.value = true;
    try {
      const response = await listMerchantOptions({
        keyword: keyword.trim() || undefined,
        status,
        limit: 100
      });
      const rows = response.data || [];
      if (requestVersion === optionRequestVersion) {
        const selected = selectedMerchant.value;
        merchants.value = selected
          ? [
              selected,
              ...rows.filter(item => String(item.id) !== String(selected.id))
            ]
          : rows;
      }
      return rows;
    } finally {
      if (requestVersion === optionRequestVersion) {
        optionsLoading.value = false;
      }
    }
  };

  const load = async () => {
    loading.value = true;
    try {
      const contextResponse = await getMerchantContext();
      context.value = contextResponse.data;
      if (context.value?.canAccessAllMerchants) {
        if (
          sessionStorage.getItem(SCOPE_STORAGE_VERSION_KEY) !==
          SCOPE_STORAGE_VERSION
        ) {
          setSelectedMerchantId();
          sessionStorage.setItem(
            SCOPE_STORAGE_VERSION_KEY,
            SCOPE_STORAGE_VERSION
          );
        }
        selectedMerchantId.value = getSelectedMerchantId();
        merchants.value = [];
        if (selectedMerchantId.value) {
          try {
            const response = await getMerchant(selectedMerchantId.value);
            if (response.data) mergeOptions([response.data]);
          } catch {
            selectedMerchantId.value = undefined;
            setSelectedMerchantId();
          }
        }
        await searchOptions();
      } else if (context.value?.merchantId != null) {
        selectedMerchantId.value = String(context.value.merchantId);
        setSelectedMerchantId(selectedMerchantId.value);
        merchants.value = [
          {
            id: context.value.merchantId,
            merchantCode: context.value.merchantCode || '',
            name: context.value.merchantName || '',
            status: '0',
            lifecycleStatus: 'ACTIVE',
            timezone: context.value.displayTimezone || 'Asia/Shanghai'
          }
        ];
      } else {
        selectedMerchantId.value = undefined;
        setSelectedMerchantId();
        merchants.value = [];
      }
    } finally {
      loading.value = false;
    }
  };

  const select = (merchantId?: string | number | null) => {
    const next =
      merchantId == null || String(merchantId).trim() === ''
        ? undefined
        : String(merchantId);
    if (next === selectedMerchantId.value) return;
    selectedMerchantId.value = next;
    setSelectedMerchantId(next);
    scopeVersion.value += 1;
    window.dispatchEvent(
      new CustomEvent('payment-merchant-changed', {
        detail: next || 'ALL'
      })
    );
  };

  const clear = () => {
    context.value = undefined;
    merchants.value = [];
    selectedMerchantId.value = undefined;
    scopeVersion.value = 0;
    optionRequestVersion += 1;
    setSelectedMerchantId();
    sessionStorage.removeItem(SCOPE_STORAGE_VERSION_KEY);
  };

  return {
    context,
    merchants,
    loading,
    optionsLoading,
    selectedMerchantId,
    selectedMerchant,
    scopeVersion,
    canAccessAllMerchants,
    isAllMerchants,
    load,
    searchOptions,
    select,
    clear
  };
});
