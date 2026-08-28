import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

export interface PublicAndroidRelease {
  id: string | number;
  versionCode: number;
  versionName: string;
  downloadUrl?: string;
  fileSize: number;
  sha256: string;
  signingCertificateSha256: string;
  releaseNotes?: string;
  publishedAt?: string;
}

export const getLatestAndroidRelease = (): AxiosPromise<PublicAndroidRelease> =>
  request({
    url: '/api/v1/public/app-releases/latest',
    method: 'get',
    params: { platform: 'ANDROID' },
    headers: {
      isToken: false
    }
  });
