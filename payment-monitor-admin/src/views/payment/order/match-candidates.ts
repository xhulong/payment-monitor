import type {
  OrderMatchCandidateVO,
  PaymentOrderVO
} from '@/api/payment/types';

export interface MatchCandidateGroup {
  label: '精确匹配' | '需强制匹配';
  candidates: OrderMatchCandidateVO[];
}

export const groupMatchCandidates = (
  candidates: OrderMatchCandidateVO[]
): MatchCandidateGroup[] => {
  const exact = candidates.filter(candidate => candidate.exactMatch);
  const forced = candidates.filter(candidate => !candidate.exactMatch);
  return [
    exact.length ? { label: '精确匹配' as const, candidates: exact } : undefined,
    forced.length
      ? { label: '需强制匹配' as const, candidates: forced }
      : undefined
  ].filter((group): group is MatchCandidateGroup => Boolean(group));
};

export const matchCandidateDifference = (
  candidate: OrderMatchCandidateVO,
  order?: PaymentOrderVO
) => {
  if (!order || candidate.exactMatch) return '平台和金额一致';
  const differences: string[] = [];
  if (candidate.platform !== order.platform) differences.push('平台不一致');
  if (candidate.amountMinor !== order.payableAmountMinor) {
    differences.push('金额不一致');
  }
  return differences.join('、') || '需强制匹配';
};

export const validateMatchCandidateSelection = (
  candidate: OrderMatchCandidateVO | undefined,
  force: boolean
) => {
  if (!candidate) return '请选择有效的支付事件';
  if (!candidate.exactMatch && !force) {
    return '平台或金额不一致，请主动开启强制匹配';
  }
  return undefined;
};
