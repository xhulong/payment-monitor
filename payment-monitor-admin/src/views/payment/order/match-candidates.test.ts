import { describe, expect, it } from 'vitest';
import type { OrderMatchCandidateVO } from '@/api/payment/types';
import {
  groupMatchCandidates,
  validateMatchCandidateSelection
} from './match-candidates';

const candidate = (
  id: number,
  exactMatch: boolean
): OrderMatchCandidateVO => ({
  id,
  clientEventId: `EVENT-${id}`,
  platform: exactMatch ? 'WECHAT' : 'ALIPAY',
  amountMinor: exactMatch ? 100 : 99,
  currency: 'CNY',
  receivedAt: '2026-07-26T10:00:00+00:00',
  status: 'RECEIVED',
  duplicateStatus: 'NONE',
  exactMatch
});

describe('人工补单候选规则', () => {
  it('按精确匹配和需强制匹配分组', () => {
    const groups = groupMatchCandidates([
      candidate(2, false),
      candidate(1, true)
    ]);
    expect(groups.map(group => group.label)).toEqual([
      '精确匹配',
      '需强制匹配'
    ]);
    expect(groups[0].candidates.map(item => item.id)).toEqual([1]);
  });

  it('异常候选必须显式开启强制匹配', () => {
    expect(validateMatchCandidateSelection(candidate(1, true), false)).toBeUndefined();
    expect(validateMatchCandidateSelection(candidate(2, false), false)).toContain(
      '开启强制匹配'
    );
    expect(validateMatchCandidateSelection(candidate(2, false), true)).toBeUndefined();
  });
});
