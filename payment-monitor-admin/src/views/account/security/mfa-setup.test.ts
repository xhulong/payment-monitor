import { describe, expect, it, vi } from 'vitest';
import { startMfaSetupFlow } from './mfa-setup';

describe('MFA setup flow', () => {
  it('requests MFA step-up before replacing an existing authenticator', async () => {
    const calls: string[] = [];
    const requestStepUp = vi.fn(async (operation: string, title?: string) => {
      calls.push(`step-up:${operation}:${title}`);
      return 'step-up-token';
    });
    const setup = vi.fn(async (token?: string) => {
      calls.push(`setup:${token}`);
      return { secret: 'secret' };
    });

    await expect(
      startMfaSetupFlow(true, requestStepUp, setup)
    ).resolves.toEqual({ secret: 'secret' });

    expect(requestStepUp).toHaveBeenCalledWith(
      'MFA_REPLACE',
      '重新配置 MFA'
    );
    expect(setup).toHaveBeenCalledWith('step-up-token');
    expect(calls).toEqual([
      'step-up:MFA_REPLACE:重新配置 MFA',
      'setup:step-up-token'
    ]);
  });

  it('starts initial MFA setup without a step-up challenge', async () => {
    const requestStepUp = vi.fn();
    const setup = vi.fn(async () => ({ secret: 'secret' }));

    await startMfaSetupFlow(false, requestStepUp, setup);

    expect(requestStepUp).not.toHaveBeenCalled();
    expect(setup).toHaveBeenCalledWith(undefined);
  });

  it('does not start replacement when the step-up dialog is cancelled', async () => {
    const requestStepUp = vi.fn().mockRejectedValue(new Error('cancel'));
    const setup = vi.fn();

    await expect(
      startMfaSetupFlow(true, requestStepUp, setup)
    ).rejects.toThrow('cancel');

    expect(setup).not.toHaveBeenCalled();
  });
});
