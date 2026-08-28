import { describe, expect, it } from 'vitest';
import {
  coreFeatures,
  faqs,
  guideSections,
  pricingPlans,
  quickGuideSteps,
  workflowSteps
} from './site-content';

describe('LuLuPay 官网内容结构', () => {
  it('包含完整工作流、核心能力和使用教程', () => {
    expect(workflowSteps).toHaveLength(4);
    expect(coreFeatures.length).toBeGreaterThanOrEqual(6);
    expect(quickGuideSteps.length).toBeGreaterThanOrEqual(5);
    expect(guideSections.map(item => item.id)).toEqual(
      expect.arrayContaining(['install', 'pairing', 'qrcode', 'test-order', 'integration', 'callback'])
    );
  });

  it('套餐只展示能力，不公开具体收费金额', () => {
    expect(pricingPlans.map(item => item.name)).toEqual([
      '免费版',
      '专业版',
      '团队版',
      '企业版'
    ]);
    expect(JSON.stringify(pricingPlans)).not.toMatch(/¥\d|元\/|每月收费/);
    expect(pricingPlans[0].actionPath).toBe('/register');
  });

  it('风险说明覆盖通知确认和官方资金确认差异', () => {
    expect(coreFeatures.some(item => item.detail.includes('不只依赖到账通知'))).toBe(true);
  });
});
