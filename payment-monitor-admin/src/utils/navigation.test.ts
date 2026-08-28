import { describe, expect, it } from 'vitest';
import { normalizeContextPath, normalizeInternalRedirect } from './navigation';

describe('normalizeContextPath', () => {
  it.each([
    [undefined, '/'],
    ['/', '/'],
    ["'/'", '/'],
    ['"/admin"', '/admin/'],
    ['admin/console', '/admin/console/']
  ])('normalizes %s to %s', (input, expected) => {
    expect(normalizeContextPath(input)).toBe(expected);
  });

  it.each(["/'/'", 'https://example.com/admin', '/../admin', '/admin?debug=1'])(
    'rejects malformed context path %s',
    input => {
      expect(normalizeContextPath(input)).toBe('/');
    }
  );
});

describe('normalizeInternalRedirect', () => {
  it.each([
    [undefined, '/index'],
    ['/index', '/index'],
    ['%2Fmerchant-center%2Forders', '/merchant-center/orders'],
    ['/merchant-center/orders?page=2#latest', '/merchant-center/orders?page=2#latest']
  ])('normalizes %s to %s', (input, expected) => {
    expect(normalizeInternalRedirect(input)).toBe(expected);
  });

  it.each([
    'https://example.com',
    '//example.com',
    '/login?redirect=/index',
    '/nested/login?redirect=/index',
    "/'/'/login?redirect=/index",
    '/%27/%27/login?redirect=/index',
    '/index\\admin'
  ])('rejects unsafe redirect %s', input => {
    expect(normalizeInternalRedirect(input)).toBe('/index');
  });
});
