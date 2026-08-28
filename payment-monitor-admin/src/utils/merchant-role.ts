import type { MerchantMemberVO } from '@/api/payment/types';

export type MerchantRoleCode = MerchantMemberVO['roleCode'];

export const merchantRoleLabels: Record<MerchantRoleCode, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  FINANCE: '财务',
  DEVELOPER: '开发者',
  VIEWER: '只读'
};

export const merchantRoleOptions = (
  Object.entries(merchantRoleLabels) as Array<[MerchantRoleCode, string]>
).map(([value, label]) => ({ value, label }));

export const editableMerchantRoleOptions = merchantRoleOptions.filter(
  option => option.value !== 'OWNER'
);

export const merchantRoleLabel = (roleCode?: string) => {
  if (!roleCode) return '-';
  return merchantRoleLabels[roleCode as MerchantRoleCode] || roleCode;
};
