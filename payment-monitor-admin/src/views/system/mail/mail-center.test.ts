import { describe, expect, it } from 'vitest';
import {
  mailOutboxRetryable,
  mailSettingsPasswordVisible
} from './mail-center';

describe('邮件中心状态规则', () => {
  it('只允许仍有效的 DEAD 邮件重试', () => {
    const now = Date.parse('2026-07-21T00:00:00Z');
    expect(mailOutboxRetryable('DEAD', '2026-07-21T01:00:00Z', now)).toBe(true);
    expect(mailOutboxRetryable('SENT', '2026-07-21T01:00:00Z', now)).toBe(false);
    expect(mailOutboxRetryable('DEAD', '2026-07-20T23:59:59Z', now)).toBe(false);
  });

  it('设置响应不得包含密码或密文', () => {
    expect(mailSettingsPasswordVisible({ passwordConfigured: true })).toBe(false);
    expect(mailSettingsPasswordVisible({ password: 'secret' })).toBe(true);
    expect(mailSettingsPasswordVisible({ passwordCiphertext: 'cipher' })).toBe(true);
  });
});
