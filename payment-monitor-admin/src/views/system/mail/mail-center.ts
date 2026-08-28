export const mailOutboxRetryable = (
  status: string,
  expiresAt?: string,
  now = Date.now()
) => status === 'DEAD' && (!expiresAt || new Date(expiresAt).getTime() > now);

export const mailSettingsPasswordVisible = (
  response: Record<string, unknown>
) => 'password' in response || 'passwordCiphertext' in response;
