import { describe, expect, it } from 'vitest';
import { isPublicRoutePath } from './public-routes';

describe('公开路由白名单', () => {
  it.each([
    '/',
    '/overview',
    '/guide',
    '/login',
    '/forgot-password',
    '/register',
    '/merchant-invitation/invite-token'
  ])('允许未登录访问 %s', path => {
    expect(isPublicRoutePath(path)).toBe(true);
  });

  it('普通业务页面仍要求登录', () => {
    expect(isPublicRoutePath('/index')).toBe(false);
  });
});
