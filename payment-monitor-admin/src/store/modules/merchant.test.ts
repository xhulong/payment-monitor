import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

const paymentApi = vi.hoisted(() => ({
  getMerchant: vi.fn(),
  getMerchantContext: vi.fn(),
  listMerchantOptions: vi.fn()
}));

vi.mock('@/api/payment', () => paymentApi);

import { useMerchantStore } from './merchant';

class MemoryStorage {
  private values = new Map<string, string>();

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  setItem(key: string, value: string) {
    this.values.set(key, String(value));
  }

  removeItem(key: string) {
    this.values.delete(key);
  }

  clear() {
    this.values.clear();
  }
}

const merchant = {
  id: 'merchant-2',
  merchantCode: 'M002',
  name: 'Merchant Two',
  status: '0' as const,
  lifecycleStatus: 'ACTIVE' as const,
  timezone: 'Asia/Shanghai'
};

describe('payment merchant store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    Object.defineProperty(globalThis, 'sessionStorage', {
      configurable: true,
      value: new MemoryStorage()
    });
    paymentApi.listMerchantOptions.mockResolvedValue({ data: [] });
  });

  it('defaults platform administrators to all merchants and clears legacy auto-selection', async () => {
    sessionStorage.setItem('payment-selected-merchant-id', 'legacy-default');
    paymentApi.getMerchantContext.mockResolvedValue({
      data: {
        superAdmin: true,
        accountType: 'PLATFORM_ADMIN',
        scopeMode: 'ALL',
        canAccessAllMerchants: true,
        displayTimezone: 'Asia/Shanghai'
      }
    });

    const store = useMerchantStore();
    await store.load();

    expect(store.selectedMerchantId).toBeUndefined();
    expect(store.isAllMerchants).toBe(true);
    expect(sessionStorage.getItem('payment-selected-merchant-id')).toBeNull();
    expect(paymentApi.getMerchant).not.toHaveBeenCalled();
  });

  it('preserves a platform administrator explicit selection in the same session', async () => {
    sessionStorage.setItem('payment-merchant-scope-storage-version', '2');
    sessionStorage.setItem('payment-selected-merchant-id', 'merchant-2');
    paymentApi.getMerchantContext.mockResolvedValue({
      data: {
        superAdmin: true,
        accountType: 'PLATFORM_ADMIN',
        scopeMode: 'ALL',
        canAccessAllMerchants: true,
        displayTimezone: 'Asia/Shanghai'
      }
    });
    paymentApi.getMerchant.mockResolvedValue({ data: merchant });
    paymentApi.listMerchantOptions.mockResolvedValue({ data: [merchant] });

    const store = useMerchantStore();
    await store.load();

    expect(store.selectedMerchantId).toBe('merchant-2');
    expect(store.selectedMerchant).toEqual(merchant);
    expect(store.isAllMerchants).toBe(false);
  });

  it('pins merchant users to their bound merchant', async () => {
    paymentApi.getMerchantContext.mockResolvedValue({
      data: {
        superAdmin: false,
        merchantId: 'merchant-11',
        merchantCode: 'M011',
        merchantName: 'Merchant Eleven',
        accountType: 'MERCHANT_USER',
        scopeMode: 'MERCHANT',
        canAccessAllMerchants: false,
        displayTimezone: 'Asia/Shanghai'
      }
    });

    const store = useMerchantStore();
    await store.load();

    expect(store.selectedMerchantId).toBe('merchant-11');
    expect(store.isAllMerchants).toBe(false);
    expect(sessionStorage.getItem('payment-selected-merchant-id')).toBe(
      'merchant-11'
    );
  });

  it('loads platform reviewers without creating a merchant scope', async () => {
    sessionStorage.setItem('payment-selected-merchant-id', 'stale-merchant');
    paymentApi.getMerchantContext.mockResolvedValue({
      data: {
        superAdmin: false,
        accountType: 'PLATFORM_ADMIN',
        scopeMode: 'ALL',
        canAccessAllMerchants: false,
        displayTimezone: 'Asia/Shanghai'
      }
    });

    const store = useMerchantStore();
    await store.load();

    expect(store.selectedMerchantId).toBeUndefined();
    expect(store.merchants).toEqual([]);
    expect(paymentApi.listMerchantOptions).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('payment-selected-merchant-id')).toBeNull();
  });
});
