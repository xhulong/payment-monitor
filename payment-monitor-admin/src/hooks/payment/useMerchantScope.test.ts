import { describe, expect, it, vi } from 'vitest';

vi.mock('@/store/modules/merchant', () => ({
  useMerchantStore: vi.fn()
}));

import { hasMixedMerchantSelection } from './useMerchantScope';

describe('payment merchant selection guard', () => {
  it('allows a single merchant batch', () => {
    expect(
      hasMixedMerchantSelection([
        { merchantId: '1' },
        { merchantId: 1 }
      ])
    ).toBe(false);
    expect(
      hasMixedMerchantSelection([
        { merchantId: 'merchant-1' },
        { merchantId: 'merchant-1' }
      ])
    ).toBe(false);
  });

  it('rejects mixed merchant batches', () => {
    expect(
      hasMixedMerchantSelection([
        { merchantId: 'merchant-1' },
        { merchantId: 'merchant-2' }
      ])
    ).toBe(true);
  });
});
