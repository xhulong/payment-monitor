const MERCHANT_INDEPENDENT_PAYMENT_PATHS = new Set([
  '/payment/merchant-context'
]);

export const normalizeApiRequestPath = (url?: string) => {
  const rawPath = String(url || '').split('?', 1)[0].trim();
  if (!rawPath) return '/';
  if (/^https?:\/\//i.test(rawPath)) {
    try {
      return new URL(rawPath).pathname || '/';
    } catch {
      return '/';
    }
  }
  return rawPath.startsWith('/') ? rawPath : `/${rawPath}`;
};

export const merchantContextHeaderValue = (
  url: string | undefined,
  selectedMerchantId: string | undefined
) => {
  if (!selectedMerchantId) return undefined;
  const requestPath = normalizeApiRequestPath(url);
  if (
    !requestPath.startsWith('/payment/') ||
    MERCHANT_INDEPENDENT_PAYMENT_PATHS.has(requestPath)
  ) {
    return undefined;
  }
  return selectedMerchantId;
};
