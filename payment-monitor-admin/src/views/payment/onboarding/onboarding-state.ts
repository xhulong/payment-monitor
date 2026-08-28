import type {
  MerchantApplicationStatus,
  MerchantOnboardingStatusVO
} from '@/api/payment/types';

export type OnboardingPanel =
  | 'APPLICATION_FORM'
  | 'APPLICATION_REVIEW'
  | 'REJECTED'
  | 'REAPPLY'
  | 'ONBOARDING_TASKS'
  | 'ACTIVE';

export interface OnboardingViewState {
  panel: OnboardingPanel;
  canEdit: boolean;
  canSubmit: boolean;
  canWithdraw: boolean;
  canReapply: boolean;
}

export const applicationStatusLabels: Record<MerchantApplicationStatus, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审核中',
  NEEDS_CHANGES: '待补充资料',
  APPROVED: '审核通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回'
};

export const merchantLifecycleLabels: Record<string, string> = {
  ONBOARDING: '待完成开通',
  ACTIVE: '已开通',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭'
};

const cooldownFinished = (cooldownUntil: string | undefined, now: number) => {
  if (!cooldownUntil) return true;
  const timestamp = new Date(cooldownUntil).getTime();
  return Number.isNaN(timestamp) || timestamp <= now;
};

export const deriveOnboardingViewState = (
  status: MerchantOnboardingStatusVO | undefined,
  now = Date.now()
): OnboardingViewState => {
  if (status?.merchantLifecycle === 'ACTIVE') {
    return {
      panel: 'ACTIVE',
      canEdit: false,
      canSubmit: false,
      canWithdraw: false,
      canReapply: false
    };
  }

  const application = status?.application;
  if (!application) {
    return {
      panel: 'APPLICATION_FORM',
      canEdit: true,
      canSubmit: true,
      canWithdraw: false,
      canReapply: false
    };
  }

  if (
    application.status === 'APPROVED' &&
    status?.merchantLifecycle === 'ONBOARDING'
  ) {
    return {
      panel: 'ONBOARDING_TASKS',
      canEdit: false,
      canSubmit: false,
      canWithdraw: false,
      canReapply: false
    };
  }

  switch (application.status) {
    case 'DRAFT':
    case 'NEEDS_CHANGES':
      return {
        panel: 'APPLICATION_FORM',
        canEdit: true,
        canSubmit: true,
        canWithdraw: false,
        canReapply: false
      };
    case 'SUBMITTED':
      return {
        panel: 'APPLICATION_REVIEW',
        canEdit: false,
        canSubmit: false,
        canWithdraw: true,
        canReapply: false
      };
    case 'UNDER_REVIEW':
    case 'APPROVED':
      return {
        panel:
          application.status === 'APPROVED'
            ? 'ONBOARDING_TASKS'
            : 'APPLICATION_REVIEW',
        canEdit: false,
        canSubmit: false,
        canWithdraw: false,
        canReapply: false
      };
    case 'REJECTED':
      return {
        panel: 'REJECTED',
        canEdit: false,
        canSubmit: false,
        canWithdraw: false,
        canReapply: cooldownFinished(application.cooldownUntil, now)
      };
    case 'WITHDRAWN':
      return {
        panel: 'REAPPLY',
        canEdit: false,
        canSubmit: false,
        canWithdraw: false,
        canReapply: true
      };
  }
};

export const calculateOnboardingProgress = (
  status: MerchantOnboardingStatusVO | undefined
) => {
  if (status?.merchantLifecycle === 'ACTIVE') return 100;
  if (status?.merchantLifecycle === 'ONBOARDING') {
    const requiredItems = status.checklist.filter(item => item.required);
    const completedItems = requiredItems.filter(item => item.completed);
    const checklistProgress =
      requiredItems.length === 0
        ? 0
        : completedItems.length / requiredItems.length;
    return Math.round(60 + checklistProgress * 40);
  }

  const applicationStatus = status?.application?.status;
  return (
    {
      DRAFT: 20,
      NEEDS_CHANGES: 25,
      SUBMITTED: 40,
      UNDER_REVIEW: 50,
      APPROVED: 60,
      REJECTED: 0,
      WITHDRAWN: 0
    }[applicationStatus || ''] || 10
  );
};
