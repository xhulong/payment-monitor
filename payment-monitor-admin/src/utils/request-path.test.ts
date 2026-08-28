import { describe, expect, it } from 'vitest';
import {
  merchantContextHeaderValue,
  normalizeApiRequestPath
} from './request-path';

describe('merchant scoped request paths', () => {
  it('normalizes relative export paths before merchant scope detection', () => {
    expect(normalizeApiRequestPath('payment/events/export')).toBe(
      '/payment/events/export'
    );
    expect(
      merchantContextHeaderValue('payment/events/export', 'merchant-200')
    ).toBe('merchant-200');
  });

  it('attaches the selected merchant to payment export requests with query parameters', () => {
    expect(
      merchantContextHeaderValue(
        '/payment/events/export?status=RECEIVED',
        'merchant-201'
      )
    ).toBe('merchant-201');
  });

  it('keeps merchant discovery and unrelated APIs independent', () => {
    expect(
      merchantContextHeaderValue(
        '/payment/merchant-context',
        'merchant-202'
      )
    ).toBeUndefined();
    expect(
      merchantContextHeaderValue('/system/user/getInfo', 'merchant-202')
    ).toBeUndefined();
  });

  it('scopes the merchant management list after an explicit top-level selection', () => {
    expect(
      merchantContextHeaderValue(
        '/payment/merchants/list',
        'merchant-203'
      )
    ).toBe('merchant-203');
  });
});
