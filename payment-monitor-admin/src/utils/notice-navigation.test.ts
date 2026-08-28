import { describe, expect, it } from 'vitest';
import {
  resolveMessageNavigationPath,
  resolveNoticeDetail
} from './notice-navigation';

describe('notice navigation', () => {
  it('opens persisted notices from their stored content instead of an admin-only route', () => {
    expect(
      resolveNoticeDetail({
        category: 'notice',
        title: '通知公告消息',
        message: '[公告] LuLuPay 近期功能更新',
        content: '<h2>近期功能更新</h2>',
        path: '/system/notice?noticeId=2084480980889055234'
      })
    ).toEqual({
      title: '通知公告消息',
      message: '[公告] LuLuPay 近期功能更新',
      content: '<h2>近期功能更新</h2>'
    });
  });

  it('uses the live push payload title and content without converting a long notice id', () => {
    expect(
      resolveNoticeDetail({
        type: 'notice',
        source: 'notice',
        message: '[公告] MFA 安全优化',
        data: {
          noticeId: '2084480980889055234',
          noticeTitle: 'MFA 安全优化',
          noticeContent: '<p>MFA 现在可以自愿启用。</p>'
        }
      })
    ).toEqual({
      title: 'MFA 安全优化',
      message: '[公告] MFA 安全优化',
      content: '<p>MFA 现在可以自愿启用。</p>'
    });
  });

  it('keeps normal internal message navigation working', () => {
    expect(resolveMessageNavigationPath('/payment/order?id=100')).toBe(
      '/payment/order?id=100'
    );
  });

  it('rejects non-string and external navigation values', () => {
    expect(resolveMessageNavigationPath(2084480980889055234n)).toBeUndefined();
    expect(
      resolveMessageNavigationPath({
        path: '/system/notice'
      })
    ).toBeUndefined();
    expect(resolveMessageNavigationPath('https://example.com')).toBeUndefined();
    expect(resolveMessageNavigationPath('//example.com')).toBeUndefined();
  });
});
