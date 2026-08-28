import { describe, expect, it, vi } from 'vitest';
import {
  runMfaSensitiveOperation,
  runOptionalMfaOperation
} from './flow';

describe('payment sensitive operation flow', () => {
  it('submits an exact match without MFA', async () => {
    const requestStepUp = vi.fn();
    const submit = vi.fn().mockResolvedValue('done');

    await expect(
      runOptionalMfaOperation(
        false,
        'PAYMENT_ORDER_FORCE_MATCH',
        '强制补单确认',
        requestStepUp,
        submit
      )
    ).resolves.toBe('done');

    expect(requestStepUp).not.toHaveBeenCalled();
    expect(submit).toHaveBeenCalledWith();
  });

  it('verifies MFA before a force match', async () => {
    const requestStepUp = vi.fn().mockResolvedValue('force-token');
    const submit = vi.fn().mockResolvedValue('done');

    await runOptionalMfaOperation(
      true,
      'PAYMENT_ORDER_FORCE_MATCH',
      '强制补单确认',
      requestStepUp,
      submit
    );

    expect(requestStepUp).toHaveBeenCalledWith(
      'PAYMENT_ORDER_FORCE_MATCH',
      '强制补单确认'
    );
    expect(submit).toHaveBeenCalledWith('force-token');
  });

  it('does not submit when the MFA dialog is cancelled', async () => {
    const requestStepUp = vi.fn().mockRejectedValue(new Error('cancel'));
    const submit = vi.fn();

    await expect(
      runMfaSensitiveOperation(
        'PAYMENT_CONFIRMATION_REVERSE',
        '撤销支付确认',
        requestStepUp,
        submit
      )
    ).rejects.toThrow('cancel');

    expect(submit).not.toHaveBeenCalled();
  });
});
