import { describe, expect, it } from 'vitest';
import type { AppReleaseVO } from '@/api/payment/types';
import {
  canDeleteAppRelease,
  findLatestPublishedId,
  toAppReleaseUpdateRequest
} from './app-release-state';

const release = (
  id: number,
  versionCode: number,
  status: AppReleaseVO['status']
): AppReleaseVO => ({
  id,
  platform: 'ANDROID',
  versionCode,
  versionName: `1.0.${versionCode}`,
  minSupportedVersionCode: 1,
  fileSize: 1,
  sha256: 'a'.repeat(64),
  signingCertificateSha256: 'b'.repeat(64),
  verificationStatus: 'VERIFIED',
  updateMode: 'OPTIONAL',
  status
});

describe('APP 版本管理状态', () => {
  it('只保护 versionCode 最高的已发布版本', () => {
    const rows = [
      release(1, 10, 'PUBLISHED'),
      release(2, 13, 'PUBLISHED'),
      release(3, 14, 'DRAFT')
    ];
    const latestId = findLatestPublishedId(rows);

    expect(latestId).toBe(2);
    expect(canDeleteAppRelease(rows[0], latestId)).toBe(true);
    expect(canDeleteAppRelease(rows[1], latestId)).toBe(false);
    expect(canDeleteAppRelease(rows[2], latestId)).toBe(true);
  });

  it('编辑时只提交更新策略和说明，不包含 APK 身份字段', () => {
    const payload = toAppReleaseUpdateRequest({
      minSupportedVersionCode: 12,
      enforcementAt: new Date('2026-07-30T00:00:00+08:00'),
      updateMode: 'REQUIRED',
      releaseNotes: ' 修复更新说明显示 '
    });

    expect(payload).toEqual({
      minSupportedVersionCode: 12,
      enforcementAt: '2026-07-29T16:00:00.000Z',
      updateMode: 'REQUIRED',
      releaseNotes: '修复更新说明显示'
    });
    expect(payload).not.toHaveProperty('signingCertificateSha256');
    expect(payload).not.toHaveProperty('sha256');
    expect(payload).not.toHaveProperty('versionCode');
  });
});
