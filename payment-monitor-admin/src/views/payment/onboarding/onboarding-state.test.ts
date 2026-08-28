import { describe, expect, it } from 'vitest';
import type {
  MerchantApplicationStatus,
  MerchantApplicationVO,
  MerchantOnboardingStatusVO
} from '@/api/payment/types';
import {
  calculateOnboardingProgress,
  deriveOnboardingViewState
} from './onboarding-state';

const application = (
  status: MerchantApplicationStatus,
  overrides: Partial<MerchantApplicationVO> = {}
): MerchantApplicationVO => ({
  id: 'application-1',
  userId: 'user-1',
  verifiedEmail: 'merchant@example.com',
  merchantDisplayName: '测试商户',
  applicantName: '测试用户',
  countryRegion: '中国',
  paymentUseCase: '线下门店通知确认',
  monthlyOrderRange: '1-100',
  monthlyAmountRange: '0-10,000',
  plannedPlatforms: 'WECHAT,ALIPAY',
  agreementVersion: '2026-07',
  privacyVersion: '2026-07',
  status,
  createdAt: '2026-07-20T09:00:00+08:00',
  updatedAt: '2026-07-20T09:00:00+08:00',
  ...overrides
});

const onboardingStatus = (
  status: MerchantApplicationStatus,
  overrides: Partial<MerchantOnboardingStatusVO> = {},
  applicationOverrides: Partial<MerchantApplicationVO> = {}
): MerchantOnboardingStatusVO => ({
  onboardingAvailable: true,
  reviewEnabled: true,
  verifiedEmail: 'merchant@example.com',
  application: application(status, applicationOverrides),
  mfaEnabled: false,
  checklist: [],
  ...overrides
});

describe('merchant onboarding view state', () => {
  it.each([
    ['DRAFT', 'APPLICATION_FORM', true, true, false],
    ['NEEDS_CHANGES', 'APPLICATION_FORM', true, true, false],
    ['SUBMITTED', 'APPLICATION_REVIEW', false, false, true],
    ['UNDER_REVIEW', 'APPLICATION_REVIEW', false, false, false],
    ['WITHDRAWN', 'REAPPLY', false, false, false]
  ] as const)(
    'maps %s to the expected panel',
    (status, panel, canEdit, canSubmit, canWithdraw) => {
      expect(deriveOnboardingViewState(onboardingStatus(status))).toMatchObject({
        panel,
        canEdit,
        canSubmit,
        canWithdraw
      });
    }
  );

  it('enables rejected reapplication only after the cooldown expires', () => {
    const now = new Date('2026-07-20T12:00:00+08:00').getTime();
    const cooling = onboardingStatus(
      'REJECTED',
      {},
      { cooldownUntil: '2026-07-21T12:00:00+08:00' }
    );
    const expired = onboardingStatus(
      'REJECTED',
      {},
      { cooldownUntil: '2026-07-19T12:00:00+08:00' }
    );

    expect(deriveOnboardingViewState(cooling, now)).toMatchObject({
      panel: 'REJECTED',
      canReapply: false
    });
    expect(deriveOnboardingViewState(expired, now)).toMatchObject({
      panel: 'REJECTED',
      canReapply: true
    });
  });

  it('shows onboarding tasks after approval and completes at ACTIVE lifecycle', () => {
    const onboarding = onboardingStatus('APPROVED', {
      merchantLifecycle: 'ONBOARDING',
      checklist: [
        { code: 'OWNER_TOTP', label: 'MFA', completed: true, required: false },
        { code: 'AGREEMENTS', label: '协议', completed: true, required: true },
        { code: 'QR_ASSET', label: '二维码', completed: false, required: true }
      ]
    });
    const active = {
      ...onboarding,
      merchantLifecycle: 'ACTIVE' as const
    };

    expect(deriveOnboardingViewState(onboarding).panel).toBe('ONBOARDING_TASKS');
    expect(calculateOnboardingProgress(onboarding)).toBe(80);
    expect(deriveOnboardingViewState(active).panel).toBe('ACTIVE');
    expect(calculateOnboardingProgress(active)).toBe(100);
  });
});
