export function formatReleaseFileSize(bytes?: number): string {
  if (!Number.isFinite(bytes) || Number(bytes) <= 0) {
    return '未知';
  }
  const value = Number(bytes);
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

export function formatReleaseDate(value?: string): string {
  if (!value) {
    return '尚未公布';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '尚未公布';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(date);
}

export function splitReleaseNotes(value?: string): string[] {
  return String(value || '')
    .split(/\r?\n/)
    .map(item => item.replace(/^[\s•*-]+/, '').trim())
    .filter(Boolean)
    .slice(0, 6);
}

export function abbreviateHash(value?: string): string {
  const hash = String(value || '').trim();
  if (!hash) {
    return '尚未提供';
  }
  if (hash.length <= 24) {
    return hash;
  }
  return `${hash.slice(0, 12)}…${hash.slice(-12)}`;
}
