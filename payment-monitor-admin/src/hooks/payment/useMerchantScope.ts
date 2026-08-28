import { computed, watch } from 'vue';
import { useMerchantStore } from '@/store/modules/merchant';

export const usePaymentMerchantScope = () => {
  const merchantStore = useMerchantStore();
  const showMerchantColumn = computed(
    () => merchantStore.canAccessAllMerchants
  );
  const selectedMerchantId = computed(
    () => merchantStore.selectedMerchantId
  );

  const defaultTargetMerchantId = () =>
    merchantStore.canAccessAllMerchants
      ? merchantStore.selectedMerchantId
      : merchantStore.context?.merchantId == null
        ? undefined
        : String(merchantStore.context.merchantId);

  const watchScope = (
    handler: () => void | Promise<void>
  ) =>
    watch(
      () => merchantStore.scopeVersion,
      () => {
        void handler();
      }
    );

  return {
    merchantStore,
    showMerchantColumn,
    selectedMerchantId,
    defaultTargetMerchantId,
    watchScope
  };
};

export const hasMixedMerchantSelection = (
  rows: Array<{ merchantId?: string | number }>
) =>
  new Set(
    rows
      .map(row => row.merchantId)
      .filter((value): value is string | number => value != null)
      .map(String)
  ).size > 1;
