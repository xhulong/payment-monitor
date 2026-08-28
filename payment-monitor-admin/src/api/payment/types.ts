export interface PaymentDashboardVO {
  displayTimezone: string;
  todayEvents: number;
  wechatEvents: number;
  alipayEvents: number;
  incomeEvents: number;
  expenseEvents: number;
  onlineDevices: number;
  todayIncomeAmountMinor: number;
  wechatIncomeAmountMinor: number;
  alipayIncomeAmountMinor: number;
  pendingReviewEvents: number;
  parseFailureRate: number;
  averageSyncLatencyMs: number;
  p95SyncLatencyMs: number;
  trend: PaymentTrendPointVO[];
}

export interface PaymentTrendPointVO {
  bucket: string;
  eventCount: number;
  incomeCount: number;
  incomeAmountMinor: number;
}

export interface ReconciliationVO {
  id?: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  runNo: string;
  businessDate: string;
  timezone: string;
  status: 'BALANCED' | 'ATTENTION_REQUIRED';
  paidOrderCount: number;
  paidOrderAmountMinor: number;
  matchedIncomeCount: number;
  matchedIncomeAmountMinor: number;
  unmatchedIncomeCount: number;
  unmatchedIncomeAmountMinor: number;
  conflictOrderCount: number;
  suspectedDuplicateCount: number;
  webhookDeadCount: number;
  amountDifferenceMinor: number;
  openDifferenceCount: number;
  resolvedDifferenceCount: number;
  version: number;
  completedAt: string;
}

export interface MerchantHealthVO {
  merchantId: string | number;
  merchantCode: string;
  merchantName: string;
  totalDevices: number;
  onlineDevices: number;
  unhealthyDevices: number;
  webhookDead: number;
  pendingReviewEvents: number;
}

export interface PaymentHomeDashboardVO {
  superAdmin: boolean;
  scopeMode: 'ALL' | 'MERCHANT';
  displayTimezone: string;
  merchantId?: string | number;
  merchantName?: string;
  merchantCount: number;
  enabledMerchantCount: number;
  todayEvents: number;
  todayPaidOrders: number;
  notificationConfirmedOrders: number;
  manuallyConfirmedOrders: number;
  reconciledOrders: number;
  sensitiveOperationsToday: number;
  openReconciliationDifferences: number;
  activeAmountSlots: number;
  coolingAmountSlots: number;
  todayIncomeAmountMinor: number;
  totalDevices: number;
  onlineDevices: number;
  unhealthyDevices: number;
  pendingReviewEvents: number;
  unmatchedIncomeEvents: number;
  conflictOrders: number;
  suspectedDuplicateEvents: number;
  webhookBacklog: number;
  webhookDead: number;
  merchantApiFailures24h: number;
  averageSyncLatencyMs: number;
  p95SyncLatencyMs: number;
  reconciliation?: ReconciliationVO;
  trend: PaymentTrendPointVO[];
  merchantHealth: MerchantHealthVO[];
}

export interface DeviceHeartbeatVO {
  id: string | number;
  heartbeatAt: string;
  appVersion?: string;
  parserVersion?: string;
  pendingCount: number;
  retryingCount: number;
  rejectedCount: number;
  lastSyncAt?: string;
  clientIp?: string;
  monitoringEnabled: boolean;
  listenerConnected: boolean;
  foregroundRunning: boolean;
  notificationAccessGranted: boolean;
  batteryOptimizationIgnored: boolean;
  lastNotificationAt?: string;
  healthIssue?: string;
}

export interface PaymentDeviceVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  deviceName: string;
  androidIdHash?: string;
  appVersion?: string;
  parserVersion?: string;
  status: string;
  pairedAt: string;
  lastSeenAt?: string;
  lastUploadAt?: string;
  pendingCount: number;
  retryingCount: number;
  rejectedCount: number;
  lastSyncAt?: string;
  lastIp?: string;
  monitoringEnabled: boolean;
  listenerConnected: boolean;
  foregroundRunning: boolean;
  notificationAccessGranted: boolean;
  batteryOptimizationIgnored: boolean;
  lastNotificationAt?: string;
  lastHealthIssue?: string;
  healthUpdatedAt?: string;
  createdAt: string;
  updatedAt: string;
  online: boolean;
  recentHeartbeats?: DeviceHeartbeatVO[];
}

export interface PaymentDeviceQuery extends PageQuery {
  merchantId?: string | number;
  deviceName?: string;
  status?: string;
  online?: boolean;
}

export interface PairingCodeVO {
  pairingSessionId: string | number;
  pairingCode: string;
  expiresAt: string;
  serverUrl: string;
  qrSchema: number;
  protocolVersion: number;
}

export interface PairingStatusVO {
  pairingSessionId: string | number;
  status: 'PENDING' | 'PAIRED' | 'EXPIRED';
  expiresAt: string;
  deviceId?: string | number;
  deviceName?: string;
  pairedAt?: string;
}

export interface DeviceStatusForm {
  status?: string;
  revokeCredential?: boolean;
}

export interface PaymentEventVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  deviceId: string | number;
  deviceSequence?: number;
  clientEventId: string;
  platform: 'WECHAT' | 'ALIPAY';
  direction: 'INCOME' | 'EXPENSE' | 'UNKNOWN';
  amountMinor?: number;
  currency: string;
  eventTime?: string;
  eventTimeMs?: number;
  clientReceivedAt: string;
  clientReceivedAtMs: number;
  clientSentAt: string;
  clientSentAtMs: number;
  receivedAt: string;
  parseStatus: 'PARSED' | 'AMOUNT_NOT_FOUND' | 'AMBIGUOUS';
  parserVersion?: string;
  matchedRule?: string;
  fingerprint: string;
  notificationKeyHash?: string;
  rawHash?: string;
  rawPayload?: string;
  status: string;
  reviewedAt?: string;
  reviewedBy?: string | number;
  reviewNote?: string;
  duplicateStatus: 'NONE' | 'SUSPECTED' | 'CONFIRMED' | 'EXCLUDED';
  duplicateOfEventId?: string | number;
  duplicateDetectedAt?: string;
  duplicateReviewedAt?: string;
  duplicateReviewedBy?: string | number;
  duplicateReviewNote?: string;
  reviewHistory?: PaymentEventReviewVO[];
}

export interface PaymentEventReviewVO {
  id: string | number;
  action: 'REVIEW' | 'CORRECT' | 'IGNORE';
  beforeStatus: string;
  afterStatus: string;
  beforeDirection?: string;
  afterDirection?: string;
  beforeAmountMinor?: number;
  afterAmountMinor?: number;
  note?: string;
  operatedBy?: string | number;
  operatedAt: string;
}

export interface PaymentEventReviewForm {
  action: 'REVIEW' | 'CORRECT' | 'IGNORE';
  direction?: 'INCOME' | 'EXPENSE' | 'UNKNOWN';
  amountMinor?: number;
  note?: string;
}

export interface PaymentEventRawVO {
  eventId: string | number;
  masked: boolean;
  rawPayload?: string;
}

export interface PaymentEventQuery extends PageQuery {
  merchantId?: string | number;
  platform?: string;
  direction?: string;
  parseStatus?: string;
  status?: string;
  duplicateStatus?: string;
  keyword?: string;
  deviceId?: string | number;
  amountMinor?: number;
  minAmountMinor?: number;
  maxAmountMinor?: number;
  beginTime?: string;
  endTime?: string;
}

export interface QrAssetVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  assetCode: string;
  platform: 'WECHAT' | 'ALIPAY';
  assetName: string;
  qrContentTemplate: string;
  status: string;
  remark?: string;
  createdAt: string;
  updatedAt: string;
}

export interface QrAssetQuery extends PageQuery {
  merchantId?: string | number;
  platform?: string;
  assetName?: string;
  status?: string;
}

export interface QrAssetForm {
  merchantId?: string | number;
  assetCode?: string;
  platform: 'WECHAT' | 'ALIPAY';
  assetName: string;
  qrContentTemplate: string;
  status: string;
  remark?: string;
}

export interface OrderMatchAuditVO {
  id: string | number;
  eventId?: string | number;
  action: string;
  beforeStatus?: string;
  afterStatus?: string;
  note?: string;
  operatedBy?: string | number;
  operatedAt: string;
}

export interface PaymentOrderVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  merchantOrderNo: string;
  platform: 'WECHAT' | 'ALIPAY';
  qrAssetId: string | number;
  qrAssetName?: string;
  requestedAmountMinor: number;
  payableAmountMinor: number;
  amountOffsetMinor: number;
  currency: string;
  status: 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED' | 'CONFLICT';
  publicToken: string;
  payUrl: string;
  subject?: string;
  customerNote?: string;
  matchedEventId?: string | number;
  transactionId?: string | number;
  confirmationStatus: 'UNCONFIRMED' | 'NOTIFICATION' | 'MANUAL' | 'RECONCILED';
  confirmedAt?: string;
  confirmedBy?: string | number;
  confirmationSource?: string;
  confirmationNote?: string;
  amountSlotStatus?: 'ACTIVE' | 'COOLING' | 'RELEASED';
  amountSlotCoolingUntil?: string;
  createdAt: string;
  expiresAt: string;
  paidAt?: string;
  cancelledAt?: string;
  updatedAt: string;
  matchHistory?: OrderMatchAuditVO[];
}

export interface PaymentOrderQuery extends PageQuery {
  merchantId?: string | number;
  merchantOrderNo?: string;
  platform?: string;
  status?: string;
  payableAmountMinor?: number;
  matchedEventId?: string | number;
  beginTime?: string;
  endTime?: string;
}

export interface PaymentOrderCreateForm {
  merchantId?: string | number;
  merchantOrderNo?: string;
  platform: 'WECHAT' | 'ALIPAY';
  qrAssetId: string | number;
  amountMinor: number;
  expiresSeconds: number;
  subject?: string;
  customerNote?: string;
}

export interface ManualOrderMatchForm {
  eventId: string | number;
  force: boolean;
  note?: string;
}

export interface OrderMatchCandidateVO {
  id: string | number;
  clientEventId?: string;
  platform: 'WECHAT' | 'ALIPAY';
  amountMinor: number;
  currency: string;
  eventTime?: string;
  receivedAt: string;
  status: 'RECEIVED' | 'REVIEWED' | 'CONFLICT';
  duplicateStatus: 'NONE' | 'SUSPECTED' | 'EXCLUDED';
  exactMatch: boolean;
}

export interface WebhookEndpointVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  endpointName: string;
  endpointUrl: string;
  status: '0' | '1';
  eventTypes: string[];
  platformFilter: 'ALL' | 'WECHAT' | 'ALIPAY';
  createdAt: string;
  updatedAt: string;
}

export interface WebhookEndpointSecretVO {
  endpoint: WebhookEndpointVO;
  webhookSecret: string;
}

export interface WebhookEndpointQuery extends PageQuery {
  merchantId?: string | number;
  endpointName?: string;
  status?: string;
}

export interface WebhookEndpointForm {
  merchantId?: string | number;
  endpointName: string;
  endpointUrl: string;
  status: '0' | '1';
  eventTypes: string[];
  platformFilter: 'ALL' | 'WECHAT' | 'ALIPAY';
}

export interface WebhookDeliveryLogVO {
  id: string | number;
  deliveryId: string;
  attemptNumber: number;
  requestAt: string;
  responseAt?: string;
  durationMs?: number;
  httpStatus?: number;
  responseExcerpt?: string;
  errorMessage?: string;
  success: boolean;
}

export interface WebhookOutboxVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  deliveryId: string;
  eventId: string;
  endpointId: string | number;
  endpointName: string;
  endpointUrl: string;
  aggregateType: string;
  aggregateId: string | number;
  eventType: string;
  status: 'PENDING' | 'DELIVERING' | 'RETRYING' | 'DELIVERED' | 'DEAD';
  attemptCount: number;
  nextAttemptAt: string;
  lockedAt?: string;
  deliveredAt?: string;
  lastHttpStatus?: number;
  lastError?: string;
  replayOfDeliveryId?: string;
  replayReason?: string;
  createdAt: string;
  updatedAt: string;
  deliveryLogs?: WebhookDeliveryLogVO[];
}

export interface MerchantApiAuditVO {
  id: string | number;
  merchantId?: string | number;
  merchantCode?: string;
  merchantName?: string;
  apiKeyId?: string | number;
  keyId?: string;
  requestMethod: string;
  requestPath: string;
  clientIp?: string;
  httpStatus: number;
  resultCode: string;
  success: boolean;
  durationMs: number;
  createdAt: string;
}

export interface WebhookOutboxQuery extends PageQuery {
  merchantId?: string | number;
  deliveryId?: string;
  status?: string;
  eventType?: string;
  aggregateId?: string | number;
  endpointId?: string | number;
}

export interface MerchantContextVO {
  superAdmin: boolean;
  merchantId?: string | number;
  merchantCode?: string;
  merchantName?: string;
  accountType: 'PLATFORM_ADMIN' | 'MERCHANT_USER';
  scopeMode: 'ALL' | 'MERCHANT';
  canAccessAllMerchants: boolean;
  displayTimezone: string;
}

export interface MerchantVO {
  id: string | number;
  merchantCode: string;
  name: string;
  status: '0' | '1';
  lifecycleStatus?: 'ONBOARDING' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  timezone: string;
  remark?: string;
  createdBy?: string | number;
  createdAt?: string;
  updatedAt?: string;
  adminUserId?: string | number;
  adminUserName?: string;
}

export interface MerchantQuery extends PageQuery {
  merchantId?: string | number;
  merchantCode?: string;
  name?: string;
  status?: string;
}

export interface MerchantForm {
  merchantCode: string;
  name: string;
  status: '0' | '1';
  timezone: string;
  remark?: string;
  adminUserId?: string | number;
  adminUserName?: string;
  adminNickName?: string;
  adminPassword?: string;
}

export interface MerchantApiKeyVO {
  id: string | number;
  merchantId: string | number;
  keyId: string;
  keyName: string;
  status: '0' | '1';
  currentVersion: number;
  lastUsedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface MerchantApiKeySecretVO {
  apiKey: MerchantApiKeyVO;
  apiSecret: string;
}

export interface DuplicateReviewForm {
  status: 'CONFIRMED' | 'EXCLUDED';
  note?: string;
}

export type TransactionStatus =
  | 'OBSERVED'
  | 'MATCHED'
  | 'CONFIRMED'
  | 'RECONCILED'
  | 'REJECTED'
  | 'REVERSED';

export type ConfirmationStatus = 'UNCONFIRMED' | 'NOTIFICATION' | 'MANUAL' | 'RECONCILED';

export interface PaymentTransactionVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  eventId: string | number;
  orderId?: string | number;
  platform: 'WECHAT' | 'ALIPAY';
  amountMinor: number;
  currency: string;
  status: TransactionStatus;
  confirmationStatus: ConfirmationStatus;
  observedAt: string;
  matchedAt?: string;
  confirmedAt?: string;
  confirmedBy?: string | number;
  reconciledAt?: string;
  reversedAt?: string;
  reversedBy?: string | number;
  rejectionReason?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentTransactionQuery extends PageQuery {
  merchantId?: string | number;
  platform?: string;
  status?: string;
  confirmationStatus?: string;
  orderId?: string | number;
  eventId?: string | number;
  beginTime?: string;
  endTime?: string;
}

export interface SensitiveOperationVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  operationType: 'FORCE_MATCH' | 'REVERSE_CONFIRMATION';
  targetType: string;
  targetId: string | number;
  reason?: string;
  requestPayload: string;
  beforeSnapshot?: string;
  afterSnapshot?: string;
  operatedBy: string | number;
  operatedAt: string;
  verificationMethod: 'MFA' | 'SESSION';
  idempotencyKey: string;
}

export interface SensitiveOperationQuery extends PageQuery {
  merchantId?: string | number;
  operationType?: string;
  targetType?: string;
  targetId?: string | number;
}

export interface ReconciliationItemVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  runId: string | number;
  differenceType: string;
  status: 'OPEN' | 'RESOLVED' | 'IGNORED';
  orderId?: string | number;
  eventId?: string | number;
  transactionId?: string | number;
  webhookOutboxId?: string | number;
  amountMinor?: number;
  description?: string;
  resolutionAction?: string;
  resolutionNote?: string;
  resolvedBy?: string | number;
  resolvedAt?: string;
  createdAt: string;
}

export interface ReconciliationRunDetailVO {
  run: ReconciliationVO;
  items: ReconciliationItemVO[];
}

export interface AmountSlotVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  platform: 'WECHAT' | 'ALIPAY';
  payableAmountMinor: number;
  orderId: string | number;
  status: 'ACTIVE' | 'COOLING' | 'RELEASED';
  reservedAt: string;
  coolingUntil?: string;
  releasedAt?: string;
  updatedAt: string;
}

export interface AmountSlotQuery extends PageQuery {
  merchantId?: string | number;
  platform?: string;
  status?: string;
  payableAmountMinor?: number;
  orderId?: string | number;
}

export interface DeviceAssignmentVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  platform: 'WECHAT' | 'ALIPAY';
  deviceId: string | number;
  deviceName?: string;
  role: 'PRIMARY' | 'BACKUP';
  priority: number;
  enabled: boolean;
  healthy: boolean;
  effectiveObserver: boolean;
  lastSeenAt?: string;
  healthIssue?: string;
}

export interface DeviceAssignmentForm {
  merchantId?: string | number;
  assignments: Array<{
    deviceId: string | number;
    platform: 'WECHAT' | 'ALIPAY';
    role: 'PRIMARY' | 'BACKUP';
    priority: number;
    enabled: boolean;
  }>;
}

export type MerchantApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'NEEDS_CHANGES'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN';

export type PlannedPaymentPlatform = 'WECHAT' | 'ALIPAY';

export interface MerchantApplicationSaveForm {
  merchantDisplayName: string;
  applicantName: string;
  phoneNumber?: string;
  countryRegion: string;
  province?: string;
  city?: string;
  paymentUseCase: string;
  monthlyOrderRange: string;
  monthlyAmountRange: string;
  plannedPlatforms: string;
  agreementVersion: string;
  privacyVersion: string;
}

export interface MerchantApplicationVO {
  id: string | number;
  userId: string | number;
  verifiedEmail: string;
  merchantDisplayName: string;
  applicantName: string;
  phoneNumber?: string;
  countryRegion: string;
  province?: string;
  city?: string;
  paymentUseCase: string;
  monthlyOrderRange: string;
  monthlyAmountRange: string;
  plannedPlatforms: string;
  agreementVersion: string;
  privacyVersion: string;
  status: MerchantApplicationStatus;
  submissionSnapshot?: string;
  reviewerId?: string | number;
  reviewNote?: string;
  submittedAt?: string;
  reviewedAt?: string;
  cooldownUntil?: string;
  merchantId?: string | number;
  createdAt: string;
  updatedAt: string;
}

export interface MerchantApplicationReviewSettingsVO {
  reviewEnabled: boolean;
}

export interface MerchantApplicationReviewSettingsForm {
  reviewEnabled: boolean;
}

export interface MerchantMemberVO {
  userId: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  username?: string;
  nickname?: string;
  email?: string;
  roleCode: 'OWNER' | 'ADMIN' | 'FINANCE' | 'DEVELOPER' | 'VIEWER';
  status: '0' | '1';
  mfaEnabled: boolean;
  joinedAt?: string;
}

export interface MerchantInvitationVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  email: string;
  roleCode: MerchantMemberVO['roleCode'];
  status: string;
  expiresAt: string;
  acceptanceToken?: string;
}

export interface AppReleaseVO {
  id: string | number;
  platform: 'ANDROID';
  versionCode: number;
  versionName: string;
  minSupportedVersionCode: number;
  enforcementAt?: string;
  downloadUrl?: string;
  fileSize: number;
  sha256: string;
  signingCertificateSha256: string;
  verifiedPackageName?: string;
  verificationStatus: 'LEGACY' | 'VERIFIED' | 'FAILED';
  updateMode: 'OPTIONAL' | 'REQUIRED' | 'SECURITY_BLOCK';
  releaseNotes?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'REVOKED';
  publishedAt?: string;
}

export interface AppReleaseUpdateRequest {
  minSupportedVersionCode: number;
  enforcementAt?: string;
  updateMode: AppReleaseVO['updateMode'];
  releaseNotes?: string;
}

export interface MerchantRegistrationVO {
  userId: string | number;
  username: string;
  email: string;
}

export interface MerchantOnboardingChecklistItem {
  code:
    | 'OWNER_TOTP'
    | 'AGREEMENTS'
    | 'QR_ASSET'
    | 'DEVICE_PAIRED'
    | 'DEVICE_ONLINE'
    | 'TEST_NOTIFICATION'
    | string;
  label: string;
  completed: boolean;
  required: boolean;
}

export interface MerchantOnboardingStatusVO {
  onboardingAvailable: boolean;
  reviewEnabled: boolean;
  verifiedEmail?: string;
  application?: MerchantApplicationVO;
  merchantId?: string | number;
  merchantCode?: string;
  merchantName?: string;
  merchantLifecycle?: 'ONBOARDING' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  memberRole?: MerchantMemberVO['roleCode'];
  mfaEnabled: boolean;
  checklist: MerchantOnboardingChecklistItem[];
}

export type PaymentIntegrationStatus = '0' | '1';
export type PaymentIntegrationNotifyMethod = 'GET' | 'POST';
export type PaymentIntegrationCallbackPolicy =
  | 'NOTIFICATION_MATCHED'
  | 'MANUAL_CONFIRMED'
  | 'RECONCILED';

export interface PaymentIntegrationSecretMetadataVO {
  id: string | number;
  secretVersion: number;
  status: 'ACTIVE' | 'RETIRED' | 'REVOKED';
  activatedAt: string;
  retiredAt?: string;
  revokedAt?: string;
}

export interface PaymentIntegrationVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  integrationCode: string;
  integrationName: string;
  protocol: 'EPAY';
  profile: 'EPAY_CLASSIC_V1';
  pid: string;
  status: PaymentIntegrationStatus;
  defaultExpireSeconds: number;
  notifyMethod: PaymentIntegrationNotifyMethod;
  callbackPolicy: PaymentIntegrationCallbackPolicy;
  allowedCallbackHosts: string[];
  remark?: string;
  activeSecretVersion?: number;
  secrets: PaymentIntegrationSecretMetadataVO[];
  createdAt: string;
  updatedAt: string;
}

export interface PaymentIntegrationSecretVO {
  integration: PaymentIntegrationVO;
  apiKey: string;
}

export interface PaymentIntegrationQuery extends PageQuery {
  merchantId?: string | number;
  integrationName?: string;
  pid?: string;
  status?: PaymentIntegrationStatus | '';
}

export interface PaymentIntegrationForm {
  merchantId?: string | number;
  integrationCode: string;
  integrationName: string;
  defaultExpireSeconds: number;
  notifyMethod: PaymentIntegrationNotifyMethod;
  callbackPolicy: PaymentIntegrationCallbackPolicy;
  allowedCallbackHosts: string[];
  remark?: string;
}

export interface PaymentIntegrationRouteVO {
  id: string | number;
  integrationId: string | number;
  payType: 'alipay' | 'wxpay';
  platform: 'ALIPAY' | 'WECHAT';
  qrAssetId: string | number;
  qrAssetName?: string;
  qrAssetCode?: string;
  priority: number;
  status: '0' | '1';
  updatedAt: string;
}

export interface PaymentIntegrationRouteForm {
  payType: PaymentIntegrationRouteVO['payType'];
  platform: PaymentIntegrationRouteVO['platform'];
  qrAssetId: string | number;
  priority: number;
  status: '0' | '1';
}

export interface ExternalOrderVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  integrationId: string | number;
  integrationName: string;
  orderId: string | number;
  internalOrderNo?: string;
  externalOrderNo: string;
  gatewayTradeNo: string;
  payType: 'alipay' | 'wxpay';
  platform?: 'ALIPAY' | 'WECHAT';
  requestAmountMinor: number;
  payableAmountMinor?: number;
  orderStatus?: string;
  confirmationStatus?: string;
  callbackPolicy: PaymentIntegrationCallbackPolicy;
  callbackStatus?: string;
  riskStatus: string;
  riskReason?: string;
  createdAt: string;
  paidAt?: string;
}

export interface ExternalOrderQuery extends PageQuery {
  merchantId?: string | number;
  integrationId?: string | number;
  externalOrderNo?: string;
  gatewayTradeNo?: string;
  riskStatus?: string;
}

export interface ProtocolCallbackDeliveryLogVO {
  id: string | number;
  outboxId: string | number;
  deliveryId: string;
  attemptNumber: number;
  requestAt: string;
  responseAt?: string;
  durationMs?: number;
  httpStatus?: number;
  responseExcerpt?: string;
  errorMessage?: string;
  acknowledged: boolean;
  createdAt: string;
}

export interface ProtocolCallbackVO {
  id: string | number;
  merchantId: string | number;
  merchantCode?: string;
  merchantName?: string;
  deliveryId: string;
  eventId: string;
  integrationId: string | number;
  integrationName: string;
  bindingId: string | number;
  externalOrderNo?: string;
  gatewayTradeNo?: string;
  requestMethod: PaymentIntegrationNotifyMethod;
  targetUrl: string;
  status: 'PENDING' | 'DELIVERING' | 'RETRYING' | 'DELIVERED' | 'DEAD';
  attemptCount: number;
  nextAttemptAt?: string;
  deliveredAt?: string;
  lastHttpStatus?: number;
  lastResponse?: string;
  lastError?: string;
  strictAcknowledged: boolean;
  replayOfId?: string | number;
  replayReason?: string;
  createdAt: string;
  deliveryLogs?: ProtocolCallbackDeliveryLogVO[];
}

export interface ProtocolCallbackQuery extends PageQuery {
  merchantId?: string | number;
  integrationId?: string | number;
  deliveryId?: string;
  status?: ProtocolCallbackVO['status'] | '';
  bindingId?: string | number;
}
