import { describe, expect, it } from 'vitest';
import {
  maskRecoveryEmail,
  passwordResetFormValid
} from './forgot-password';

describe('找回密码流程', () => {
  it('只展示脱敏后的邮箱地址', () => {
    expect(maskRecoveryEmail('owner@example.com')).toBe('o***@example.com');
  });

  it('要求 12–64 位且两次密码一致', () => {
    expect(passwordResetFormValid('123456789012', '123456789012')).toBe(true);
    expect(passwordResetFormValid('short', 'short')).toBe(false);
    expect(passwordResetFormValid('123456789012', '123456789013')).toBe(false);
  });
});
