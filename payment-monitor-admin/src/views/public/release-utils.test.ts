import { describe, expect, it } from 'vitest';
import {
  abbreviateHash,
  formatReleaseDate,
  formatReleaseFileSize,
  splitReleaseNotes
} from './release-utils';

describe('官网 Android 发布信息格式化', () => {
  it('按字节大小输出适合阅读的单位', () => {
    expect(formatReleaseFileSize(512)).toBe('512 B');
    expect(formatReleaseFileSize(1536)).toBe('1.5 KB');
    expect(formatReleaseFileSize(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(formatReleaseFileSize(0)).toBe('未知');
  });

  it('格式化有效日期并处理空值', () => {
    expect(formatReleaseDate()).toBe('尚未公布');
    expect(formatReleaseDate('invalid')).toBe('尚未公布');
    expect(formatReleaseDate('2026-07-21T08:00:00+08:00')).toMatch(/2026/);
  });

  it('拆分并清理更新说明', () => {
    expect(splitReleaseNotes('- 修复监听\n* 优化配对\n\n• 完善更新')).toEqual([
      '修复监听',
      '优化配对',
      '完善更新'
    ]);
  });

  it('在页面上缩略展示长校验值', () => {
    const hash = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
    expect(abbreviateHash(hash)).toBe('0123456789ab…456789abcdef');
    expect(abbreviateHash('')).toBe('尚未提供');
  });
});
