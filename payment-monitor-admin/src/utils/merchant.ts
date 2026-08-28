const STORAGE_KEY = 'payment-selected-merchant-id';

export const getSelectedMerchantId = (): string | undefined => {
  const value = sessionStorage.getItem(STORAGE_KEY);
  return value || undefined;
};

export const setSelectedMerchantId = (merchantId?: string | number) => {
  if (merchantId == null || String(merchantId).trim() === '') {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, String(merchantId));
};
