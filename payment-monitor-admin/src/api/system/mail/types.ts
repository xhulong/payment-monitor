export type MailSecurityMode = 'SSL' | 'STARTTLS' | 'NONE';

export interface MailSettingsVO {
  enabled: boolean;
  host: string;
  port: number;
  authEnabled: boolean;
  username?: string;
  passwordConfigured: boolean;
  fromName: string;
  fromAddress: string;
  securityMode: MailSecurityMode;
  connectionTimeoutMs: number;
  readTimeoutMs: number;
  source: 'DATABASE' | 'ENVIRONMENT';
  updatedAt?: string;
}

export interface MailSettingsForm {
  enabled: boolean;
  host: string;
  port: number;
  authEnabled: boolean;
  username?: string;
  password?: string;
  clearPassword?: boolean;
  fromName: string;
  fromAddress: string;
  securityMode: MailSecurityMode;
  connectionTimeoutMs: number;
  readTimeoutMs: number;
}

export interface MailOutboxVO {
  id: string | number;
  messageId: string;
  messageType: string;
  maskedRecipient: string;
  subject: string;
  status: 'PENDING' | 'RETRYING' | 'SENT' | 'DEAD' | 'CANCELLED';
  attemptCount: number;
  maxAttempts: number;
  nextAttemptAt?: string;
  expiresAt?: string;
  sentAt?: string;
  lastError?: string;
  createdAt: string;
  updatedAt: string;
  retryable: boolean;
}

export interface MailOutboxQuery extends PageQuery {
  status?: string;
  messageType?: string;
  startTime?: string;
  endTime?: string;
}
