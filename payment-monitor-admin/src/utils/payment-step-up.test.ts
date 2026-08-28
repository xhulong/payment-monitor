import { beforeEach, describe, expect, it, vi } from 'vitest';

const apiMocks = vi.hoisted(() => ({
  getTotpStatus: vi.fn(),
  createStepUpToken: vi.fn()
}));

const prompt = vi.hoisted(() => vi.fn());

vi.mock('@/api/payment', () => apiMocks);
vi.mock('element-plus', () => ({
  ElMessageBox: { prompt }
}));

import { requestPaymentStepUp } from './payment-step-up';

describe('requestPaymentStepUp', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns without prompting when MFA is disabled', async () => {
    apiMocks.getTotpStatus.mockResolvedValue({ data: false });

    await expect(
      requestPaymentStepUp('PAYMENT_ORDER_FORCE_MATCH')
    ).resolves.toBeUndefined();

    expect(prompt).not.toHaveBeenCalled();
    expect(apiMocks.createStepUpToken).not.toHaveBeenCalled();
  });

  it('prompts and creates an operation-bound token when MFA is enabled', async () => {
    apiMocks.getTotpStatus.mockResolvedValue({ data: true });
    prompt.mockResolvedValue({ value: '123456' });
    apiMocks.createStepUpToken.mockResolvedValue({
      data: {
        token: 'step-up-token',
        operation: 'PAYMENT_ORDER_FORCE_MATCH',
        expiresAt: '2026-08-04T10:00:00+08:00'
      }
    });

    await expect(
      requestPaymentStepUp('PAYMENT_ORDER_FORCE_MATCH', '强制补单')
    ).resolves.toBe('step-up-token');

    expect(prompt).toHaveBeenCalledTimes(1);
    expect(apiMocks.createStepUpToken).toHaveBeenCalledWith(
      'PAYMENT_ORDER_FORCE_MATCH',
      '123456'
    );
  });

  it('stops the operation when MFA status cannot be loaded', async () => {
    apiMocks.getTotpStatus.mockRejectedValue(
      new Error('MFA status unavailable')
    );

    await expect(
      requestPaymentStepUp('PAYMENT_CONFIRMATION_REVERSE')
    ).rejects.toThrow('MFA status unavailable');

    expect(prompt).not.toHaveBeenCalled();
    expect(apiMocks.createStepUpToken).not.toHaveBeenCalled();
  });

  it('does not create a token when the MFA prompt is cancelled', async () => {
    apiMocks.getTotpStatus.mockResolvedValue({ data: true });
    prompt.mockRejectedValue(new Error('cancel'));

    await expect(
      requestPaymentStepUp('PAYMENT_CONFIRMATION_REVERSE')
    ).rejects.toThrow('cancel');

    expect(apiMocks.createStepUpToken).not.toHaveBeenCalled();
  });
});
