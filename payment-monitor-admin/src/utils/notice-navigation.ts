import { NOTICE_GROUP, PUSH_MESSAGE_SOURCE, PUSH_MESSAGE_TYPE } from '@/utils/push-message';

export interface NoticeNavigationItem {
  title?: string;
  category?: string;
  type?: string;
  source?: string;
  message?: string;
  content?: string;
  data?: Record<string, unknown> | null;
  path?: unknown;
}

export interface NoticeDetail {
  title: string;
  message: string;
  content: string;
}

const stringValue = (value: unknown) =>
  typeof value === 'string' ? value.trim() : '';

export const resolveNoticeDetail = (
  item?: NoticeNavigationItem | null
): NoticeDetail | undefined => {
  if (!item) {
    return undefined;
  }
  const isNotice =
    item.category === NOTICE_GROUP.NOTICE ||
    item.type === PUSH_MESSAGE_TYPE.NOTICE ||
    item.source === PUSH_MESSAGE_SOURCE.NOTICE;
  if (!isNotice) {
    return undefined;
  }

  const data = item.data ?? {};
  return {
    title:
      stringValue(data.noticeTitle) ||
      stringValue(item.title) ||
      '通知公告',
    message: stringValue(item.message),
    content:
      stringValue(item.content) ||
      stringValue(data.noticeContent)
  };
};

export const resolveMessageNavigationPath = (
  value: unknown
): string | undefined => {
  if (typeof value !== 'string') {
    return undefined;
  }
  const path = value.trim();
  if (!path.startsWith('/') || path.startsWith('//')) {
    return undefined;
  }
  return path;
};
