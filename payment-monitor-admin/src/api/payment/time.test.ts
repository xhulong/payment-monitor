import { describe, expect, it } from 'vitest';
import { formatApiTime } from './time';

describe('API ISO 时间格式化', () => {
  it('正确处理带时区的 OffsetDateTime', () => {
    const formatted = formatApiTime('2026-07-26T10:11:12.345+00:00');
    expect(formatted).not.toBe('0-0-0 0:0:0');
    expect(formatted).toContain('2026');
    expect(formatted).toContain('.345');
  });

  it('空值显示短横线', () => {
    expect(formatApiTime()).toBe('-');
    expect(formatApiTime('')).toBe('-');
  });
});
