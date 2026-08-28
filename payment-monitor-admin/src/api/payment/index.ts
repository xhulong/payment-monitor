import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  DeviceStatusForm,
  PairingCodeVO,
  PairingStatusVO,
  PaymentDashboardVO,
  PaymentDeviceQuery,
  PaymentDeviceVO,
  PaymentEventQuery,
  PaymentEventRawVO,
  PaymentEventReviewForm,
  PaymentEventVO,
  PaymentOrderCreateForm,
  PaymentOrderQuery,
  PaymentOrderVO,
  OrderMatchCandidateVO,
  QrAssetForm,
  QrAssetQuery,
  QrAssetVO,
  ManualOrderMatchForm,
  WebhookEndpointForm,
  WebhookEndpointQuery,
  WebhookEndpointSecretVO,
  WebhookEndpointVO,
  WebhookOutboxQuery,
  WebhookOutboxVO
  ,MerchantContextVO
  ,MerchantVO
  ,MerchantQuery
  ,MerchantForm
  ,MerchantApiKeyVO
  ,MerchantApiKeySecretVO
  ,DuplicateReviewForm
  ,PaymentHomeDashboardVO
  ,ReconciliationVO
  ,MerchantApiAuditVO
  ,PaymentTransactionVO
  ,PaymentTransactionQuery
  ,SensitiveOperationVO
  ,SensitiveOperationQuery
  ,ReconciliationRunDetailVO
  ,ReconciliationItemVO
  ,AmountSlotVO
  ,AmountSlotQuery
  ,DeviceAssignmentVO
  ,DeviceAssignmentForm
  ,MerchantApplicationVO
  ,MerchantApplicationSaveForm
  ,MerchantApplicationReviewSettingsVO
  ,MerchantApplicationReviewSettingsForm
  ,MerchantMemberVO
  ,MerchantInvitationVO
  ,AppReleaseVO
  ,AppReleaseUpdateRequest
  ,MerchantRegistrationVO
  ,MerchantOnboardingStatusVO
  ,ExternalOrderQuery
  ,ExternalOrderVO
  ,PaymentIntegrationForm
  ,PaymentIntegrationQuery
  ,PaymentIntegrationRouteForm
  ,PaymentIntegrationRouteVO
  ,PaymentIntegrationSecretVO
  ,PaymentIntegrationStatus
  ,PaymentIntegrationVO
  ,ProtocolCallbackQuery
  ,ProtocolCallbackVO
} from './types';

export const getPaymentHomeDashboard = (): AxiosPromise<PaymentHomeDashboardVO> =>
  request({ url: '/payment/home-dashboard', method: 'get' });

export const runPaymentReconciliation = (
  businessDate?: string,
  merchantId?: string | number
): AxiosPromise<ReconciliationVO> =>
  request({
    url: '/payment/reconciliation/run',
    method: 'post',
    params: { businessDate, merchantId }
  });

export const listMerchantApiAudits = (
  query: PageQuery & { merchantId?: string | number }
): AxiosPromise<PageResult<MerchantApiAuditVO>> =>
  request({ url: '/payment/merchant-api-audits/list', method: 'get', params: query });

export const getPaymentDashboard = (): AxiosPromise<PaymentDashboardVO> => {
  return request({
    url: '/payment/dashboard',
    method: 'get'
  });
};

export const listPaymentDevices = (query: PaymentDeviceQuery): AxiosPromise<PageResult<PaymentDeviceVO>> => {
  return request({
    url: '/payment/devices/list',
    method: 'get',
    params: query
  });
};

export const getPaymentDevice = (id: string | number): AxiosPromise<PaymentDeviceVO> => {
  return request({
    url: `/payment/devices/${id}`,
    method: 'get'
  });
};

export const createPairingCode = (
  merchantId?: string | number
): AxiosPromise<PairingCodeVO> => {
  return request({
    url: '/payment/pairing-codes',
    method: 'post',
    params: { merchantId }
  });
};

export const getPairingStatus = (
  pairingSessionId: string | number
): AxiosPromise<PairingStatusVO> => {
  return request({
    url: `/payment/pairing-codes/${pairingSessionId}/status`,
    method: 'get'
  });
};

export const updatePaymentDeviceStatus = (id: string | number, data: DeviceStatusForm) => {
  return request({
    url: `/payment/devices/${id}/status`,
    method: 'put',
    data
  });
};

export const batchUpdatePaymentDeviceStatus = (
  ids: Array<string | number>,
  status: '0' | '1'
) => request({
  url: '/payment/devices/batch-status',
  method: 'put',
  data: { ids, status }
});

export const listPaymentEvents = (query: PaymentEventQuery): AxiosPromise<PageResult<PaymentEventVO>> => {
  return request({
    url: '/payment/events/list',
    method: 'get',
    params: query
  });
};

export const getPaymentEvent = (id: string | number): AxiosPromise<PaymentEventVO> => {
  return request({
    url: `/payment/events/${id}`,
    method: 'get'
  });
};

export const reviewPaymentEvent = (
  id: string | number,
  data: PaymentEventReviewForm
): AxiosPromise<PaymentEventVO> => {
  return request({
    url: `/payment/events/${id}/review`,
    method: 'put',
    data
  });
};

export const batchReviewPaymentEvents = (
  ids: Array<string | number>,
  action: 'REVIEW' | 'IGNORE',
  note?: string
) => request({
  url: '/payment/events/batch-review',
  method: 'put',
  data: { ids, action, note }
});

export const getPaymentEventRaw = (
  id: string | number,
  full = false
): AxiosPromise<PaymentEventRawVO> => {
  return request({
    url: `/payment/events/${id}/raw${full ? '/full' : ''}`,
    method: 'get'
  });
};

export const listQrAssets = (query: QrAssetQuery): AxiosPromise<PageResult<QrAssetVO>> => {
  return request({
    url: '/payment/qr-assets/list',
    method: 'get',
    params: query
  });
};

export const listEnabledQrAssets = (
  platform?: string,
  merchantId?: string | number
): AxiosPromise<QrAssetVO[]> => {
  return request({
    url: '/payment/qr-assets/enabled',
    method: 'get',
    params: { platform, merchantId }
  });
};

export const createQrAsset = (data: QrAssetForm): AxiosPromise<QrAssetVO> => {
  return request({
    url: '/payment/qr-assets',
    method: 'post',
    data
  });
};

export const updateQrAsset = (
  id: string | number,
  data: QrAssetForm
): AxiosPromise<QrAssetVO> => {
  return request({
    url: `/payment/qr-assets/${id}`,
    method: 'put',
    data
  });
};

export const batchUpdateQrAssetStatus = (
  ids: Array<string | number>,
  status: '0' | '1'
) => request({
  url: '/payment/qr-assets/batch-status',
  method: 'put',
  data: { ids, status }
});

export const deleteQrAssets = (ids: Array<string | number>) =>
  request({
    url: '/payment/qr-assets',
    method: 'delete',
    data: { ids }
  });

export const listPaymentOrders = (
  query: PaymentOrderQuery
): AxiosPromise<PageResult<PaymentOrderVO>> => {
  return request({
    url: '/payment/orders/list',
    method: 'get',
    params: query
  });
};

export const getPaymentOrder = (id: string | number): AxiosPromise<PaymentOrderVO> => {
  return request({
    url: `/payment/orders/${id}`,
    method: 'get'
  });
};

export const createPaymentOrder = (
  data: PaymentOrderCreateForm
): AxiosPromise<PaymentOrderVO> => {
  return request({
    url: '/payment/orders',
    method: 'post',
    data
  });
};

export const cancelPaymentOrder = (
  id: string | number,
  note?: string
): AxiosPromise<PaymentOrderVO> => {
  return request({
    url: `/payment/orders/${id}/cancel`,
    method: 'put',
    params: { note }
  });
};

export const batchCancelPaymentOrders = (
  ids: Array<string | number>,
  note?: string
) => request({
  url: '/payment/orders/batch-cancel',
  method: 'put',
  data: { ids, note }
});

export const manualMatchPaymentOrder = (
  id: string | number,
  data: ManualOrderMatchForm,
  stepUpToken?: string
): AxiosPromise<PaymentOrderVO> => {
  return request({
    url: `/payment/orders/${id}/match`,
    method: 'put',
    data,
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });
};

export const listPaymentOrderMatchCandidates = (
  id: string | number
): AxiosPromise<OrderMatchCandidateVO[]> => {
  return request({
    url: `/payment/orders/${id}/match-candidates`,
    method: 'get'
  });
};

export const listWebhookEndpoints = (
  query: WebhookEndpointQuery
): AxiosPromise<PageResult<WebhookEndpointVO>> => {
  return request({
    url: '/payment/webhooks/endpoints/list',
    method: 'get',
    params: query
  });
};

export const getWebhookEndpoint = (
  id: string | number
): AxiosPromise<WebhookEndpointVO> => {
  return request({
    url: `/payment/webhooks/endpoints/${id}`,
    method: 'get'
  });
};

export const createWebhookEndpoint = (
  data: WebhookEndpointForm
): AxiosPromise<WebhookEndpointSecretVO> => {
  return request({
    url: '/payment/webhooks/endpoints',
    method: 'post',
    data
  });
};

export const updateWebhookEndpoint = (
  id: string | number,
  data: WebhookEndpointForm
): AxiosPromise<WebhookEndpointVO> => {
  return request({
    url: `/payment/webhooks/endpoints/${id}`,
    method: 'put',
    data
  });
};

export const batchUpdateWebhookEndpointStatus = (
  ids: Array<string | number>,
  status: '0' | '1'
) => request({
  url: '/payment/webhooks/endpoints/batch-status',
  method: 'put',
  data: { ids, status }
});

export const deleteWebhookEndpoints = (ids: Array<string | number>) =>
  request({
    url: '/payment/webhooks/endpoints',
    method: 'delete',
    data: { ids }
  });

export const rotateWebhookSecret = (
  id: string | number,
  stepUpToken?: string
): AxiosPromise<WebhookEndpointSecretVO> => {
  return request({
    url: `/payment/webhooks/endpoints/${id}/rotate-secret`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });
};

export const testWebhookEndpoint = (
  id: string | number
): AxiosPromise<WebhookOutboxVO> =>
  request({ url: `/payment/webhooks/endpoints/${id}/test`, method: 'post' });

export const listWebhookOutbox = (
  query: WebhookOutboxQuery
): AxiosPromise<PageResult<WebhookOutboxVO>> => {
  return request({
    url: '/payment/webhooks/outbox/list',
    method: 'get',
    params: query
  });
};

export const getWebhookOutbox = (
  id: string | number
): AxiosPromise<WebhookOutboxVO> => {
  return request({
    url: `/payment/webhooks/outbox/${id}`,
    method: 'get'
  });
};

export const retryWebhookOutbox = (
  id: string | number
): AxiosPromise<WebhookOutboxVO> => {
  return request({
    url: `/payment/webhooks/outbox/${id}/retry`,
    method: 'put'
  });
};

export const batchRetryWebhookOutbox = (ids: Array<string | number>) =>
  request({
    url: '/payment/webhooks/outbox/batch-retry',
    method: 'put',
    data: { ids }
  });

export const replayWebhookOutbox = (
  id: string | number,
  reason?: string
): AxiosPromise<WebhookOutboxVO> =>
  request({
    url: `/payment/webhooks/outbox/${id}/replay`,
    method: 'post',
    data: { reason }
  });

export const getMerchantContext = (): AxiosPromise<MerchantContextVO> =>
  request({ url: '/payment/merchant-context', method: 'get' });

export const listMerchants = (
  query: MerchantQuery
): AxiosPromise<PageResult<MerchantVO>> =>
  request({ url: '/payment/merchants/list', method: 'get', params: query });

export const listMerchantOptions = (query?: {
  keyword?: string;
  status?: string;
  limit?: number;
}): AxiosPromise<MerchantVO[]> =>
  request({ url: '/payment/merchants/options', method: 'get', params: query });

export const getMerchant = (id: string | number): AxiosPromise<MerchantVO> =>
  request({ url: `/payment/merchants/${id}`, method: 'get' });

export const createMerchant = (data: MerchantForm): AxiosPromise<MerchantVO> =>
  request({ url: '/payment/merchants', method: 'post', data });

export const updateMerchant = (
  id: string | number,
  data: MerchantForm
): AxiosPromise<MerchantVO> =>
  request({ url: `/payment/merchants/${id}`, method: 'put', data });

export const bindMerchantUser = (
  id: string | number,
  userId: string | number
): AxiosPromise<MerchantVO> =>
  request({ url: `/payment/merchants/${id}/admin-user`, method: 'put', data: { userId } });

export const listMerchantApiKeys = (
  merchantId: string | number
): AxiosPromise<MerchantApiKeyVO[]> =>
  request({ url: `/payment/merchants/${merchantId}/api-keys`, method: 'get' });

export const createMerchantApiKey = (
  merchantId: string | number,
  keyName: string,
  stepUpToken?: string
): AxiosPromise<MerchantApiKeySecretVO> =>
  request({
    url: `/payment/merchants/${merchantId}/api-keys`,
    method: 'post',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined,
    data: { keyName }
  });

export const rotateMerchantApiKey = (
  merchantId: string | number,
  keyId: string | number,
  stepUpToken?: string
): AxiosPromise<MerchantApiKeySecretVO> =>
  request({
    url: `/payment/merchants/${merchantId}/api-keys/${keyId}/rotate`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const revokeMerchantApiKey = (
  merchantId: string | number,
  keyId: string | number,
  stepUpToken?: string
): AxiosPromise<MerchantApiKeyVO> =>
  request({
    url: `/payment/merchants/${merchantId}/api-keys/${keyId}/revoke`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const reviewDuplicatePaymentEvent = (
  id: string | number,
  data: DuplicateReviewForm
): AxiosPromise<PaymentEventVO> =>
  request({ url: `/payment/events/${id}/duplicate-review`, method: 'put', data });

export const listPaymentTransactions = (
  query: PaymentTransactionQuery
): AxiosPromise<PageResult<PaymentTransactionVO>> =>
  request({ url: '/payment/transactions/list', method: 'get', params: query });

export const getPaymentTransaction = (
  id: string | number
): AxiosPromise<PaymentTransactionVO> =>
  request({ url: `/payment/transactions/${id}`, method: 'get' });

export const confirmPaymentTransaction = (
  id: string | number,
  note?: string
): AxiosPromise<PaymentTransactionVO> =>
  request({ url: `/payment/transactions/${id}/confirm`, method: 'put', data: { note } });

export const reversePaymentTransaction = (
  id: string | number,
  reason: string,
  stepUpToken?: string
): AxiosPromise<PaymentTransactionVO> =>
  request({
    url: `/payment/transactions/${id}/reverse`,
    method: 'put',
    data: { reason },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const listSensitiveOperations = (
  query: SensitiveOperationQuery
): AxiosPromise<PageResult<SensitiveOperationVO>> =>
  request({ url: '/payment/sensitive-operations/list', method: 'get', params: query });

export const getSensitiveOperation = (
  id: string | number
): AxiosPromise<SensitiveOperationVO> =>
  request({ url: `/payment/sensitive-operations/${id}`, method: 'get' });

export const listReconciliationRuns = (
  query: PageQuery & { merchantId?: string | number }
): AxiosPromise<PageResult<ReconciliationVO>> =>
  request({ url: '/payment/reconciliation/runs/list', method: 'get', params: query });

export const getReconciliationRun = (
  id: string | number
): AxiosPromise<ReconciliationRunDetailVO> =>
  request({ url: `/payment/reconciliation/runs/${id}`, method: 'get' });

export const createReconciliationRun = (
  businessDate?: string,
  merchantId?: string | number
): AxiosPromise<ReconciliationVO> =>
  request({
    url: '/payment/reconciliation/runs',
    method: 'post',
    params: { businessDate, merchantId }
  });

export const resolveReconciliationItem = (
  id: string | number,
  action: 'RESOLVE' | 'IGNORE' | 'RECONCILE',
  note?: string
): AxiosPromise<ReconciliationItemVO> =>
  request({ url: `/payment/reconciliation/items/${id}/resolve`, method: 'post', data: { action, note } });

export const listAmountSlots = (
  query: AmountSlotQuery
): AxiosPromise<PageResult<AmountSlotVO>> =>
  request({ url: '/payment/amount-slots/list', method: 'get', params: query });

export const listDeviceAssignments = (
  merchantId?: string | number
): AxiosPromise<DeviceAssignmentVO[]> =>
  request({ url: '/payment/device-assignments', method: 'get', params: { merchantId } });

export const saveDeviceAssignments = (
  data: DeviceAssignmentForm
): AxiosPromise<DeviceAssignmentVO[]> =>
  request({ url: '/payment/device-assignments', method: 'put', data });

export const sendMerchantRegistrationEmailCode = (data: {
  email: string;
  captchaUuid: string;
  captchaCode: string;
}): AxiosPromise<void> =>
  request({
    url: '/api/v1/public/merchant-accounts/email-code',
    method: 'post',
    headers: { isToken: false },
    data
  });

export const registerMerchantAccount = (data: {
  username: string;
  email: string;
  nickname: string;
  password: string;
  emailCode: string;
  invitationToken?: string;
}): AxiosPromise<MerchantRegistrationVO> =>
  request({
    url: '/api/v1/public/merchant-accounts/register',
    method: 'post',
    headers: { isToken: false },
    data
  });

export const getMerchantOnboardingStatus = (): AxiosPromise<MerchantOnboardingStatusVO> =>
  request({ url: '/api/v1/merchant-onboarding/status', method: 'get' });

export const createMerchantApplication = (
  data: MerchantApplicationSaveForm
): AxiosPromise<MerchantApplicationVO> =>
  request({ url: '/api/v1/merchant-onboarding/applications', method: 'post', data });

export const updateMerchantApplication = (
  id: string | number,
  data: MerchantApplicationSaveForm
): AxiosPromise<MerchantApplicationVO> =>
  request({ url: `/api/v1/merchant-onboarding/applications/${id}`, method: 'put', data });

export const submitMerchantApplication = (
  id: string | number
): AxiosPromise<MerchantApplicationVO> =>
  request({ url: `/api/v1/merchant-onboarding/applications/${id}/submit`, method: 'post' });

export const withdrawMerchantApplication = (
  id: string | number
): AxiosPromise<MerchantApplicationVO> =>
  request({ url: `/api/v1/merchant-onboarding/applications/${id}/withdraw`, method: 'post' });

export const listMerchantApplications = (
  query: PageQuery & { status?: string }
): AxiosPromise<PageResult<MerchantApplicationVO>> =>
  request({
    url: '/payment/platform/merchant-applications/list',
    method: 'get',
    params: query
  });

export const getMerchantApplicationReviewSettings =
  (): AxiosPromise<MerchantApplicationReviewSettingsVO> =>
    request({
      url: '/payment/platform/merchant-applications/review-settings',
      method: 'get'
    });

export const updateMerchantApplicationReviewSettings = (
  data: MerchantApplicationReviewSettingsForm,
  stepUpToken?: string
): AxiosPromise<MerchantApplicationReviewSettingsVO> =>
  request({
    url: '/payment/platform/merchant-applications/review-settings',
    method: 'put',
    headers: {
      ...(stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : {}),
      repeatSubmit: false
    },
    data
  });

export const claimMerchantApplication = (
  id: string | number
): AxiosPromise<MerchantApplicationVO> =>
  request({ url: `/payment/platform/merchant-applications/${id}/claim`, method: 'put' });

export const approveMerchantApplication = (
  id: string | number,
  note: string,
  stepUpToken?: string
): AxiosPromise<MerchantApplicationVO> =>
  request({
    url: `/payment/platform/merchant-applications/${id}/approve`,
    method: 'put',
    data: { note },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const requestMerchantApplicationChanges = (
  id: string | number,
  note: string,
  stepUpToken?: string
): AxiosPromise<MerchantApplicationVO> =>
  request({
    url: `/payment/platform/merchant-applications/${id}/request-changes`,
    method: 'put',
    data: { note },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const rejectMerchantApplication = (
  id: string | number,
  note: string,
  stepUpToken?: string
): AxiosPromise<MerchantApplicationVO> =>
  request({
    url: `/payment/platform/merchant-applications/${id}/reject`,
    method: 'put',
    data: { note },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const listMerchantMembers = (
  merchantId?: string | number
): AxiosPromise<MerchantMemberVO[]> =>
  request({ url: '/payment/merchant/members', method: 'get', params: { merchantId } });

export const listMerchantInvitations = (
  merchantId?: string | number
): AxiosPromise<MerchantInvitationVO[]> =>
  request({ url: '/payment/merchant/invitations', method: 'get', params: { merchantId } });

export const createMerchantInvitation = (data: {
  merchantId?: string | number;
  email: string;
  roleCode: MerchantMemberVO['roleCode'];
}, stepUpToken?: string): AxiosPromise<MerchantInvitationVO> =>
  request({
    url: '/payment/merchant/invitations',
    method: 'post',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined,
    data
  });

export const updateMerchantMember = (
  userId: string | number,
  data: { roleCode?: MerchantMemberVO['roleCode']; status?: '0' | '1' },
  stepUpToken?: string
): AxiosPromise<MerchantMemberVO> =>
  request({
    url: `/payment/merchant/members/${userId}/role`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined,
    data
  });

export const removeMerchantMember = (userId: string | number, stepUpToken?: string) =>
  request({
    url: `/payment/merchant/members/${userId}`,
    method: 'delete',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const batchUpdateMerchantMemberStatus = (
  userIds: Array<string | number>,
  status: '0' | '1',
  stepUpToken?: string
) => request({
  url: '/payment/merchant/members/batch-status',
  method: 'put',
  headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined,
  data: { ids: userIds, status }
});

export const batchRemoveMerchantMembers = (
  userIds: Array<string | number>,
  stepUpToken?: string
) => request({
  url: '/payment/merchant/members',
  method: 'delete',
  headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined,
  data: { ids: userIds }
});

export const acceptMerchantInvitation = (
  token: string
): AxiosPromise<MerchantMemberVO> =>
  request({
    url: `/api/v1/merchant-invitations/${encodeURIComponent(token)}/accept`,
    method: 'post'
  });

export const listAppReleases = (
  query: PageQuery
): AxiosPromise<PageResult<AppReleaseVO>> =>
  request({ url: '/payment/app-releases/list', method: 'get', params: query });

export const uploadAppRelease = (
  data: FormData,
  stepUpToken?: string
): AxiosPromise<AppReleaseVO> =>
  request({
    url: '/payment/app-releases',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : {})
    }
  });

export const publishAppRelease = (
  id: string | number,
  stepUpToken?: string
): AxiosPromise<AppReleaseVO> =>
  request({
    url: `/payment/app-releases/${id}/publish`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const updateAppRelease = (
  id: string | number,
  data: AppReleaseUpdateRequest,
  stepUpToken?: string
): AxiosPromise<AppReleaseVO> =>
  request({
    url: `/payment/app-releases/${id}`,
    method: 'put',
    data,
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const deleteAppReleases = (
  ids: Array<string | number>,
  stepUpToken?: string
) => request({
  url: '/payment/app-releases',
  method: 'delete',
  headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined,
  data: { ids }
});

export const sendPasswordResetCode = (data: {
  email: string;
  captchaUuid: string;
  captchaCode: string;
}): AxiosPromise<void> =>
  request({
    url: '/api/v1/public/accounts/password-reset/code',
    method: 'post',
    headers: {
      isToken: false,
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data
  });

export const confirmPasswordReset = (data: {
  email: string;
  code: string;
  newPassword: string;
}): AxiosPromise<void> =>
  request({
    url: '/api/v1/public/accounts/password-reset/confirm',
    method: 'post',
    headers: {
      isToken: false,
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data
  });

export const sendEmailChangeCode = (
  data: {
    newEmail: string;
    password: string;
  },
  stepUpToken?: string
): AxiosPromise<void> =>
  request({
    url: '/account/email-change/code',
    method: 'post',
    headers: {
      ...(stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : {}),
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data
  });

export const confirmEmailChange = (data: {
  newEmail: string;
  code: string;
}): AxiosPromise<void> =>
  request({
    url: '/account/email-change/confirm',
    method: 'post',
    headers: {
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data
  });

export const setupTotp = (
  stepUpToken?: string
): AxiosPromise<{ secret: string; otpauthUri: string }> =>
  request({
    url: '/account/mfa/totp/setup',
    method: 'post',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const getTotpStatus = (): AxiosPromise<boolean> =>
  request({ url: '/account/mfa/status', method: 'get' });

export const confirmTotp = (code: string): AxiosPromise<string[]> =>
  request({ url: '/account/mfa/totp/confirm', method: 'post', data: { code } });

export const regenerateTotpRecoveryCodes = (stepUpToken?: string): AxiosPromise<string[]> =>
  request({
    url: '/account/mfa/totp/recovery-codes',
    method: 'post',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const disableTotp = (stepUpToken?: string): AxiosPromise<void> =>
  request({
    url: '/account/mfa/totp',
    method: 'delete',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const createStepUpToken = (
  operation: string,
  code: string
): AxiosPromise<{ token: string; operation: string; expiresAt: string }> =>
  request({ url: '/auth/step-up/totp', method: 'post', data: { operation, code } });

export const listPaymentIntegrations = (
  query: PaymentIntegrationQuery
): AxiosPromise<PageResult<PaymentIntegrationVO>> =>
  request({ url: '/payment/integrations', method: 'get', params: query });

export const getPaymentIntegration = (
  id: string | number
): AxiosPromise<PaymentIntegrationVO> =>
  request({ url: `/payment/integrations/${id}`, method: 'get' });

export const createPaymentIntegration = (
  data: PaymentIntegrationForm,
  stepUpToken?: string
): AxiosPromise<PaymentIntegrationSecretVO> =>
  request({
    url: '/payment/integrations',
    method: 'post',
    data,
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const updatePaymentIntegration = (
  id: string | number,
  data: PaymentIntegrationForm,
  stepUpToken?: string
): AxiosPromise<PaymentIntegrationVO> =>
  request({
    url: `/payment/integrations/${id}`,
    method: 'put',
    data,
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const updatePaymentIntegrationStatus = (
  id: string | number,
  status: PaymentIntegrationStatus,
  stepUpToken?: string
): AxiosPromise<PaymentIntegrationVO> =>
  request({
    url: `/payment/integrations/${id}/status`,
    method: 'put',
    data: { status },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const rotatePaymentIntegrationSecret = (
  id: string | number,
  stepUpToken?: string
): AxiosPromise<PaymentIntegrationSecretVO> =>
  request({
    url: `/payment/integrations/${id}/secrets/rotate`,
    method: 'post',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const revokePaymentIntegrationSecret = (
  id: string | number,
  secretId: string | number,
  stepUpToken?: string
): AxiosPromise<PaymentIntegrationVO> =>
  request({
    url: `/payment/integrations/${id}/secrets/${secretId}/revoke`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const listPaymentIntegrationRoutes = (
  id: string | number
): AxiosPromise<PaymentIntegrationRouteVO[]> =>
  request({ url: `/payment/integrations/${id}/routes`, method: 'get' });

export const savePaymentIntegrationRoutes = (
  id: string | number,
  routes: PaymentIntegrationRouteForm[],
  stepUpToken?: string
): AxiosPromise<PaymentIntegrationRouteVO[]> =>
  request({
    url: `/payment/integrations/${id}/routes`,
    method: 'put',
    data: { routes },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const listExternalOrders = (
  query: ExternalOrderQuery
): AxiosPromise<PageResult<ExternalOrderVO>> =>
  request({ url: '/payment/external-orders/list', method: 'get', params: query });

export const getExternalOrder = (
  id: string | number
): AxiosPromise<ExternalOrderVO> =>
  request({ url: `/payment/external-orders/${id}`, method: 'get' });

export const listProtocolCallbacks = (
  query: ProtocolCallbackQuery
): AxiosPromise<PageResult<ProtocolCallbackVO>> =>
  request({ url: '/payment/protocol-callbacks/list', method: 'get', params: query });

export const getProtocolCallback = (
  id: string | number
): AxiosPromise<ProtocolCallbackVO> =>
  request({ url: `/payment/protocol-callbacks/${id}`, method: 'get' });

export const retryProtocolCallback = (
  id: string | number,
  stepUpToken?: string
): AxiosPromise<ProtocolCallbackVO> =>
  request({
    url: `/payment/protocol-callbacks/${id}/retry`,
    method: 'put',
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });

export const replayProtocolCallback = (
  id: string | number,
  reason: string,
  stepUpToken?: string
): AxiosPromise<ProtocolCallbackVO> =>
  request({
    url: `/payment/protocol-callbacks/${id}/replay`,
    method: 'post',
    data: { reason },
    headers: stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : undefined
  });
