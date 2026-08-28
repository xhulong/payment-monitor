import { describe, expect, it } from 'vitest';
import {
  callbackPolicyLabel,
  callbackStatusLabel,
  canRetryProtocolCallback,
  splitCallbackHosts,
  toRouteForm
} from './integration-state';

describe('易支付管理端状态辅助逻辑', () => {
  it('明确区分三种确认策略', () => {
    expect(callbackPolicyLabel('NOTIFICATION_MATCHED')).toBe('通知匹配后确认');
    expect(callbackPolicyLabel('MANUAL_CONFIRMED')).toBe('人工确认后通知');
    expect(callbackPolicyLabel('RECONCILED')).toBe('完成对账后通知');
  });

  it('只有 DEAD 回调允许人工重试', () => {
    expect(canRetryProtocolCallback({ status: 'DEAD' })).toBe(true);
    expect(canRetryProtocolCallback({ status: 'RETRYING' })).toBe(false);
    expect(callbackStatusLabel('DELIVERED')).toBe('已送达');
  });

  it('规范化并去重回调域名', () => {
    expect(
      splitCallbackHosts('Pay.Example.com\napi.example.com，pay.example.com')
    ).toEqual(['pay.example.com', 'api.example.com']);
  });

  it('保存路由时不携带服务端展示字段', () => {
    expect(
      toRouteForm({
        id: '1',
        integrationId: '2',
        payType: 'wxpay',
        platform: 'WECHAT',
        qrAssetId: '3',
        qrAssetName: '微信主码',
        qrAssetCode: 'wx-main',
        priority: 10,
        status: '0',
        updatedAt: '2026-07-20T00:00:00Z'
      })
    ).toEqual({
      payType: 'wxpay',
      platform: 'WECHAT',
      qrAssetId: '3',
      priority: 10,
      status: '0'
    });
  });
});
