import type {
  AppReleaseUpdateRequest,
  AppReleaseVO
} from '@/api/payment/types';

export interface AppReleaseMetadataForm {
  minSupportedVersionCode: number;
  enforcementAt?: Date | string;
  updateMode: AppReleaseVO['updateMode'];
  releaseNotes: string;
}

export function findLatestPublishedId(
  releases: AppReleaseVO[]
): string | number | undefined {
  return releases
    .filter(item => item.status === 'PUBLISHED')
    .reduce<AppReleaseVO | undefined>(
      (latest, item) =>
        !latest || item.versionCode > latest.versionCode ? item : latest,
      undefined
    )?.id;
}

export function canDeleteAppRelease(
  release: AppReleaseVO,
  latestPublishedId?: string | number
): boolean {
  return (
    latestPublishedId === undefined ||
    String(release.id) !== String(latestPublishedId)
  );
}

export function toAppReleaseUpdateRequest(
  form: AppReleaseMetadataForm
): AppReleaseUpdateRequest {
  const enforcementAt =
    form.enforcementAt instanceof Date
      ? form.enforcementAt.toISOString()
      : String(form.enforcementAt || '').trim() || undefined;
  const releaseNotes = form.releaseNotes.trim() || undefined;
  return {
    minSupportedVersionCode: form.minSupportedVersionCode,
    enforcementAt,
    updateMode: form.updateMode,
    releaseNotes
  };
}

export function appReleaseStatusLabel(status: AppReleaseVO['status']): string {
  return {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    REVOKED: '已撤销'
  }[status];
}

export function appReleaseUpdateModeLabel(
  mode: AppReleaseVO['updateMode']
): string {
  return {
    OPTIONAL: '可选更新',
    REQUIRED: '要求更新',
    SECURITY_BLOCK: '安全阻断'
  }[mode];
}
