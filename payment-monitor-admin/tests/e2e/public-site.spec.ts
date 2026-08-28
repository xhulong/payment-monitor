import { expect, test, type Page } from '@playwright/test';

const releasePayload = {
  id: 'release-1',
  versionCode: 12,
  versionName: '1.0.0',
  downloadUrl: '/downloads/lulupay-1.0.0.apk',
  fileSize: 5 * 1024 * 1024,
  sha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
  signingCertificateSha256:
    'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
  releaseNotes: '优化通知监听\n完善设备配对',
  publishedAt: '2026-07-21T08:00:00+08:00'
};

async function mockRefresh(page: Page) {
  await page.route('**/auth/refresh', route =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 401, msg: 'no refresh session' })
    })
  );
}

test.beforeEach(async ({ page }) => {
  await mockRefresh(page);
});

test('未登录可以访问官网首页并跳转教程和注册', async ({ page }) => {
  await page.route('**/api/v1/public/app-releases/latest**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: releasePayload })
    })
  );

  await page.setViewportSize({ width: 1366, height: 768 });
  await page.goto('/');

  await expect(page).toHaveTitle('LuLuPay - 码支付通知监控与业务回调');
  await expect(page.locator('h1')).toContainText('把收款通知');
  await expect(page.getByText('系统介绍')).toHaveCount(0);
  await expect(page.getByText('免费开始使用').first()).toBeVisible();
  await expect(page.getByText('查看使用教程').first()).toBeVisible();
  await expect(page.locator('[data-testid="release-card"]')).toContainText('LuLuPay v1.0.0');
  await expect(page.locator('meta[name="description"]')).toHaveAttribute(
    'content',
    /Android 收款通知监听/
  );

  const pageSize = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    page: document.documentElement.scrollWidth
  }));
  expect(pageSize.page).toBeLessThanOrEqual(pageSize.viewport);

  await page.getByText('查看使用教程').first().click();
  await expect(page).toHaveURL(/\/guide$/);
  await expect(page.locator('h1')).toContainText('第一笔回调');
  const registerButton = page.getByRole('link', { name: '免费创建账号' });
  await expect(registerButton).toBeVisible();
  const registerButtonColors = await registerButton.evaluate(element => {
    const style = window.getComputedStyle(element);
    return {
      color: style.color,
      backgroundColor: style.backgroundColor
    };
  });
  expect(registerButtonColors.color).not.toBe(registerButtonColors.backgroundColor);

  await page.getByRole('link', { name: '登录' }).first().click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page).toHaveTitle('LuLuPay - 码支付');
});

test('/overview 展示最新版官网首页并保留页内导航', async ({ page }) => {
  await page.route('**/api/v1/public/app-releases/latest**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: releasePayload })
    })
  );

  await page.setViewportSize({ width: 1702, height: 805 });
  await page.goto('/overview');

  await expect(page).toHaveURL(/\/overview$/);
  await expect(page).toHaveTitle('LuLuPay - 码支付通知监控与业务回调');
  await expect(page.locator('h1')).toContainText('把收款通知');
  await expect(page.getByText('可靠地接入')).toBeVisible();
  await expect(page.getByText('免费开始使用').first()).toBeVisible();

  await page.getByRole('button', { name: '工作原理' }).click();
  await expect(page).toHaveURL(/\/overview#workflow$/);
  await expect(page.locator('#workflow')).toBeInViewport();
});

test('Android 下载前重新获取临时地址', async ({ page }) => {
  let releaseRequests = 0;
  await page.route('**/api/v1/public/app-releases/latest**', route => {
    releaseRequests += 1;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: releasePayload })
    });
  });
  await page.route('**/downloads/lulupay-1.0.0.apk', route =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="lulupay-1.0.0.apk"'
      },
      body: 'fake-apk'
    })
  );

  await page.goto('/#download');
  await expect(page.locator('[data-testid="release-card"]')).toContainText('最新正式版');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: '下载 Android 监控端' }).click();
  const download = await downloadPromise;

  expect(download.suggestedFilename()).toBe('lulupay-1.0.0.apk');
  expect(releaseRequests).toBe(2);
});

test('没有发布版本时不提供失效下载按钮', async ({ page }) => {
  await page.route('**/api/v1/public/app-releases/latest**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: null })
    })
  );

  await page.goto('/#download');
  await expect(page.locator('[data-testid="release-card"]')).toContainText('监控端暂未发布');
  await expect(page.getByRole('button', { name: '下载 Android 监控端' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '查看安装教程' })).toBeVisible();
});

test('发布接口异常时显示可恢复的错误状态', async ({ page }) => {
  await page.route('**/api/v1/public/app-releases/latest**', route =>
    route.abort('connectionfailed')
  );

  await page.goto('/#download');
  await expect(page.locator('[data-testid="release-card"]')).toContainText(
    '暂时无法获取版本信息'
  );
  await expect(page.getByRole('button', { name: '重新获取' })).toBeVisible();
  await expect(page.getByRole('link', { name: '先查看安装教程' })).toBeVisible();
});

test('移动端导航可打开并进入下载区域', async ({ page }) => {
  await page.route('**/api/v1/public/app-releases/latest**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: releasePayload })
    })
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  await page.getByRole('button', { name: '切换网站导航' }).click();
  await expect(page.locator('.site-nav')).toHaveClass(/is-open/);
  await page.locator('.site-nav').getByRole('button', { name: '下载监控端' }).click();
  await expect(page).toHaveURL(/#download$/);
  await expect(page.locator('#download')).toBeInViewport();
});
