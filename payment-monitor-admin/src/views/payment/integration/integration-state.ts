import type {
  PaymentIntegrationCallbackPolicy,
  PaymentIntegrationRouteForm,
  PaymentIntegrationRouteVO,
  ProtocolCallbackVO
} from '@/api/payment/types';

export const EPAY_STEP_UP_OPERATION = 'EPAY_INTEGRATION_WRITE';

export const callbackPolicyLabel = (
  value?: PaymentIntegrationCallbackPolicy
) =>
  ({
    NOTIFICATION_MATCHED: '通知匹配后确认',
    MANUAL_CONFIRMED: '人工确认后通知',
    RECONCILED: '完成对账后通知'
  })[value || 'NOTIFICATION_MATCHED'];

export const confirmationStatusLabel = (value?: string) =>
  ({
    UNCONFIRMED: '未确认',
    NOTIFICATION: '通知确认',
    MANUAL: '人工确认',
    RECONCILED: '对账确认'
  })[value || ''] || value || '-';

export const callbackStatusLabel = (value?: string) =>
  ({
    PENDING: '等待投递',
    DELIVERING: '投递中',
    RETRYING: '等待重试',
    DELIVERED: '已送达',
    DEAD: '已停止重试'
  })[value || ''] || value || '-';

export const callbackStatusType = (
  value?: string
): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
  const statusTypes: Record<
    string,
    'success' | 'warning' | 'danger' | 'info' | 'primary'
  > = {
    PENDING: 'info',
    DELIVERING: 'primary',
    RETRYING: 'warning',
    DELIVERED: 'success',
    DEAD: 'danger'
  };
  return statusTypes[value || ''] || 'info';
};

export const canRetryProtocolCallback = (
  callback?: Pick<ProtocolCallbackVO, 'status'>
) => callback?.status === 'DEAD';

export const toRouteForm = (
  route: PaymentIntegrationRouteVO
): PaymentIntegrationRouteForm => ({
  payType: route.payType,
  platform: route.platform,
  qrAssetId: route.qrAssetId,
  priority: route.priority,
  status: route.status
});

export const splitCallbackHosts = (value: string) =>
  Array.from(
    new Set(
      value
        .split(/[\n,，\s]+/)
        .map(item => item.trim().toLowerCase())
        .filter(Boolean)
    )
  );
