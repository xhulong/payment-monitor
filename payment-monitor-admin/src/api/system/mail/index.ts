import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  MailOutboxQuery,
  MailOutboxVO,
  MailSettingsForm,
  MailSettingsVO
} from './types';

export const getMailSettings = (): AxiosPromise<MailSettingsVO> =>
  request({
    url: '/system/mail-settings',
    method: 'get'
  });

export const updateMailSettings = (
  data: MailSettingsForm,
  stepUpToken?: string
): AxiosPromise<MailSettingsVO> =>
  request({
    url: '/system/mail-settings',
    method: 'put',
    headers: {
      ...(stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : {}),
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data
  });

export const testMailSettings = (
  recipient: string,
  stepUpToken?: string
): AxiosPromise<void> =>
  request({
    url: '/system/mail-settings/test',
    method: 'post',
    headers: {
      ...(stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : {}),
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data: { recipient }
  });

export const listMailOutbox = (
  query: MailOutboxQuery
): AxiosPromise<PageResult<MailOutboxVO>> =>
  request({
    url: '/system/mail-outbox/list',
    method: 'get',
    params: query
  });

export const getMailOutbox = (
  id: string | number
): AxiosPromise<MailOutboxVO> =>
  request({
    url: `/system/mail-outbox/${id}`,
    method: 'get'
  });

export const retryMailOutbox = (
  id: string | number,
  stepUpToken?: string
): AxiosPromise<MailOutboxVO> =>
  request({
    url: `/system/mail-outbox/${id}/retry`,
    method: 'put',
    headers: {
      ...(stepUpToken ? { 'X-Step-Up-Token': stepUpToken } : {}),
      repeatSubmit: false
    },
    apiCryptoV2: 'request-response',
    data: {}
  });
