import { describe, expect, it } from 'vitest';
import {
  editableMerchantRoleOptions,
  merchantRoleLabel,
  merchantRoleOptions
} from './merchant-role';

describe('商户岗位中文映射', () => {
  it('显示统一中文岗位名称', () => {
    expect(merchantRoleLabel('OWNER')).toBe('所有者');
    expect(merchantRoleLabel('ADMIN')).toBe('管理员');
    expect(merchantRoleLabel('FINANCE')).toBe('财务');
    expect(merchantRoleLabel('DEVELOPER')).toBe('开发者');
    expect(merchantRoleLabel('VIEWER')).toBe('只读');
  });

  it('所有岗位可邀请，但修改岗位时排除所有者', () => {
    expect(merchantRoleOptions.map(item => item.value)).toEqual([
      'OWNER',
      'ADMIN',
      'FINANCE',
      'DEVELOPER',
      'VIEWER'
    ]);
    expect(editableMerchantRoleOptions.map(item => item.value)).toEqual([
      'ADMIN',
      'FINANCE',
      'DEVELOPER',
      'VIEWER'
    ]);
  });
});
