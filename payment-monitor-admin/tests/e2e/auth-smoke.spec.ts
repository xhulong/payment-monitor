import { expect, test } from '@playwright/test';
import {
  constants,
  createCipheriv,
  createDecipheriv,
  createHash,
  createHmac,
  generateKeyPairSync,
  privateDecrypt,
  randomBytes
} from 'node:crypto';

const API_CRYPTO_CONTENT_TYPE = 'application/vnd.paymentmonitor.crypto+json';
const API_CRYPTO_KID = 'payment-monitor-e2e-rsa-1';
const API_CRYPTO_PATH = '/auth/login';
const apiCryptoKeyPair = generateKeyPairSync('rsa', { modulusLength: 2048 });
const apiCryptoPublicJwk = apiCryptoKeyPair.publicKey.export({ format: 'jwk' });

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v2/crypto/jwks', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        activeKid: API_CRYPTO_KID,
        keys: [
          {
            kty: 'RSA',
            kid: API_CRYPTO_KID,
            use: 'enc',
            alg: 'RSA-OAEP-256',
            n: apiCryptoPublicJwk.n,
            e: apiCryptoPublicJwk.e
          }
        ]
      })
    })
  );
  await page.route('**/auth/refresh', route =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 401, msg: 'no refresh session' })
    })
  );
  await page.route('**/auth/code', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: { captchaEnabled: false, img: '', uuid: 'e2e-captcha' }
      })
    })
  );
});

test('login exposes merchant registration entry', async ({ page }) => {
  await page.goto('/login');

  await expect(page).toHaveTitle('LuLuPay - 码支付');
  await expect(page.locator('.brand-mark')).toHaveAttribute('alt', 'LuLuPay Logo');
  await expect(page.locator('.brand-name')).toHaveText('LuLuPay');
  await expect(page.locator('.brand-kicker')).toHaveText('码支付');
  await expect(page.getByText('局域网优先')).toHaveCount(0);
  await expect(page.getByText('数据按商户隔离')).toBeVisible();
  await expect(page.locator('.login-footer')).toContainText('© LuLuPay · 码支付');
  await expect(page.locator('#login-username')).toBeVisible();
  await expect(page.locator('#login-password')).toBeVisible();
  await expect(page.locator('.register-link')).toBeVisible();
  await expect(page.locator('.divider')).toHaveCount(0);
  await expect(page.locator('.social-actions')).toHaveCount(0);
});

test('registration and password recovery pages use LuLuPay brand', async ({
  page
}) => {
  await page.goto('/register');
  await expect(page.locator('.intro-brand img')).toHaveAttribute('alt', 'LuLuPay Logo');
  await expect(page.locator('.intro-brand strong')).toHaveText('LuLuPay');
  await expect(page.locator('.intro-brand span')).toHaveText('码支付');
  await expect(page.locator('.brand-footer')).toHaveText('© LuLuPay · 码支付');

  await page.goto('/forgot-password');
  await expect(page.locator('.brand img')).toHaveAttribute('alt', 'LuLuPay Logo');
  await expect(page.locator('.brand strong')).toHaveText('LuLuPay');
  await expect(page.locator('.brand span')).toHaveText('码支付');
  await expect(page.locator('.brand-footer')).toHaveText('© LuLuPay · 码支付');
});

test('1366x768 login layout fits the viewport without vertical scrolling', async ({
  page
}) => {
  await page.setViewportSize({ width: 1366, height: 768 });
  await page.route('**/auth/code', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          captchaEnabled: true,
          img: 'R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==',
          uuid: 'e2e-captcha-visible'
        }
      })
    })
  );

  await page.goto('/login');
  await expect(page.locator('.auth-panel')).toBeVisible();
  await expect(page.locator('.captcha-image')).toBeVisible();

  const layout = await page.evaluate(() => {
    const authPanel = document.querySelector('.auth-panel')?.getBoundingClientRect();
    const footer = document.querySelector('.login-footer')?.getBoundingClientRect();
    return {
      viewportHeight: window.innerHeight,
      scrollHeight: document.documentElement.scrollHeight,
      authPanelBottom: authPanel?.bottom || 0,
      footerTop: footer?.top || 0
    };
  });

  expect(layout.scrollHeight).toBeLessThanOrEqual(layout.viewportHeight);
  expect(layout.authPanelBottom).toBeLessThan(layout.footerTop);
});

test('merchant registration entry opens the self-service form', async ({ page }) => {
  await page.goto('/login');
  await page.locator('.register-link').click();

  await expect(page).toHaveURL(/\/register$/);
  await expect(page.locator('input[type="email"]')).toBeVisible();
  await expect(page.locator('.back-link')).toBeVisible();
});

test('merchant registration only requires the image captcha when sending the email code', async ({
  page
}) => {
  await page.unroute('**/auth/code');
  await page.route('**/auth/code', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          captchaEnabled: true,
          img: 'R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==',
          uuid: 'registration-captcha'
        }
      })
    })
  );

  await page.route('**/api/v1/public/merchant-accounts/email-code', route => {
    expect(route.request().postDataJSON()).toEqual({
      email: 'merchant@example.com',
      captchaUuid: 'registration-captcha',
      captchaCode: 'abcd'
    });
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: null })
    });
  });

  await page.route('**/api/v1/public/merchant-accounts/register', route => {
    const request = route.request().postDataJSON();
    expect(request).toEqual({
      username: 'merchant_test',
      email: 'merchant@example.com',
      nickname: '测试商户',
      password: 'correct-horse-battery-staple',
      emailCode: '123456'
    });
    expect(request).not.toHaveProperty('captchaCode');
    expect(request).not.toHaveProperty('captchaUuid');
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          userId: 'user-1',
          username: 'merchant_test',
          email: 'merchant@example.com'
        }
      })
    });
  });

  await page.goto('/register');
  const formItems = page.locator('.register-form .el-form-item');
  await formItems.filter({ hasText: '登录用户名' }).locator('input').fill('merchant_test');
  await formItems.filter({ hasText: '昵称' }).locator('input').fill('测试商户');
  await formItems.filter({ hasText: '邮箱地址' }).locator('input').fill('merchant@example.com');
  await formItems.filter({ hasText: '图形验证码' }).locator('input').fill('abcd');
  await page.getByRole('button', { name: '发送验证码' }).click();

  await expect(page.getByText('注册时无需再次输入图形验证码')).toBeVisible();
  await expect(formItems.filter({ hasText: '图形验证码' })).toHaveCount(0);

  await formItems.filter({ hasText: '邮箱验证码' }).locator('input').fill('123456');
  const passwordInputs = page.locator('.register-form input[type="password"]');
  await passwordInputs.nth(0).fill('correct-horse-battery-staple');
  await passwordInputs.nth(1).fill('correct-horse-battery-staple');
  await page.getByRole('button', { name: /注册并进入入驻向导/ }).click();

  await expect(page).toHaveURL(/\/login\?account=merchant%40example\.com&registered=1$/);
});

test('login blocks on submit when WebCrypto is unavailable without exposing page details', async ({
  page
}) => {
  let jwksRequests = 0;
  await page.addInitScript(() => {
    Object.defineProperty(Crypto.prototype, 'subtle', {
      configurable: true,
      get: () => undefined
    });
    Object.defineProperty(window, 'isSecureContext', {
      configurable: true,
      value: false
    });
  });
  await page.route('**/api/v2/crypto/jwks', route => {
    jwksRequests += 1;
    return route.fulfill({ status: 500 });
  });

  await page.goto('/login');

  await expect(page.getByText('当前访问环境暂不支持安全登录，请使用安全连接后重试')).toHaveCount(0);
  await expect(page.locator('.submit-button')).toBeEnabled();
  await page.locator('.submit-button').click();
  await expect(page.locator('.el-message')).toHaveText(
    '当前访问环境暂不支持安全登录，请使用安全连接后重试'
  );
  expect(jwksRequests).toBe(0);
});

test('required MFA login uses api-crypto-v2 and opens the challenge', async ({ page }) => {
  await page.route('**/auth/login', async route => {
    const request = route.request();
    const headers = request.headers();
    const envelope = request.postDataJSON() as ApiCryptoEnvelope;

    expect(headers['x-api-crypto-version']).toBe('2');
    expect(headers['content-type']).toContain(API_CRYPTO_CONTENT_TYPE);
    expect(envelope.v).toBe(2);
    expect(envelope.kid).toBe(API_CRYPTO_KID);
    expect(envelope.wrappedKey).toBeTruthy();
    expect(envelope.iv).toBeTruthy();
    expect(envelope.ciphertext).toBeTruthy();
    expect(envelope.tag).toBeTruthy();
    expect(request.postData()).not.toContain('mfa-user');
    expect(request.postData()).not.toContain('correct-horse-battery-staple');

    const { masterKey, plaintext } = decryptRequestEnvelope(envelope);
    expect(plaintext.username).toBe('mfa-user');
    expect(plaintext.password).toBe('correct-horse-battery-staple');

    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': API_CRYPTO_CONTENT_TYPE,
        'X-Api-Crypto-Version': '2'
      },
      body: JSON.stringify(encryptResponseEnvelope(envelope, masterKey, {
        code: 200,
        data: {
          mfaRequired: true,
          mfaSetupRequired: false,
          mfaChallengeToken: '0123456789abcdef0123456789abcdef'
        }
      }))
    });
  });

  await page.goto('/login');
  await page.locator('#login-username').fill('mfa-user');
  await page.locator('#login-password').fill('correct-horse-battery-staple');
  await page.locator('.submit-button').click();

  await expect(page.locator('#login-mfa-code')).toBeVisible();
  await expect(page.locator('.submit-button')).toBeVisible();
  await expect(page.locator('.mfa-back')).toBeVisible();
});

test('home quick action opens the V13 nested payment route without entering 404', async ({
  page
}) => {
  let orderStatus: 'PENDING' | 'PAID' = 'PENDING';
  let submittedMatch: { eventId?: string; force?: boolean } | undefined;
  await page.route('**/auth/login', async route => {
    const envelope = route.request().postDataJSON() as ApiCryptoEnvelope;
    const { masterKey } = decryptRequestEnvelope(envelope);
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': API_CRYPTO_CONTENT_TYPE,
        'X-Api-Crypto-Version': '2'
      },
      body: JSON.stringify(
        encryptResponseEnvelope(envelope, masterKey, {
          code: 200,
          data: {
            access_token: 'home-route-access-token',
            mfaRequired: false,
            mfaSetupRequired: false
          }
        })
      )
    });
  });
  await page.route('**/system/user/getInfo', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          user: {
            userId: 'user-1',
            userName: 'merchant-user',
            nickName: '测试商户用户',
            avatarUrl: ''
          },
          roles: ['merchant_owner'],
          permissions: [
            'payment:dashboard:view',
            'payment:order:list',
            'payment:order:match'
          ]
        }
      })
    })
  );
  await page.route('**/system/menu/getRouters', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: [
          {
            path: '/payment',
            component: 'Layout',
            name: 'Payment',
            meta: { title: '支付管理' },
            children: [
              {
                path: 'payment-operations',
                component: 'ParentView',
                name: 'PaymentOperations',
                meta: { title: '支付运营' },
                children: [
                  {
                    path: 'order',
                    component: 'payment/order/index',
                    name: 'PaymentOrder',
                    meta: { title: '支付订单' }
                  }
                ]
              }
            ]
          }
        ]
      })
    })
  );
  await page.route('**/payment/merchant-context', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          superAdmin: false,
          merchantId: 'merchant-1',
          merchantCode: 'M000001',
          merchantName: '测试商户'
        }
      })
    })
  );
  await page.route('**/payment/home-dashboard', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          superAdmin: false,
          merchantId: 'merchant-1',
          merchantName: '测试商户',
          trend: [],
          merchantHealth: []
        }
      })
    })
  );
  await page.route('**/resource/message**', route =>
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: ''
    })
  );
  await page.route('**/resource/message/box', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: []
      })
    })
  );
  await page.route('**/api/v1/merchant-onboarding/status', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          verifiedEmail: 'merchant@example.com',
          merchantId: 'merchant-1',
          merchantCode: 'M000001',
          merchantName: '测试商户',
          merchantLifecycle: 'ACTIVE',
          memberRole: 'OWNER',
          mfaEnabled: true,
          checklist: []
        }
      })
    })
  );
  await page.route('**/payment/orders/list**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          rows: [
            {
              id: 'order-1',
              merchantId: 'merchant-1',
              merchantOrderNo: 'ORDER-001',
              platform: 'WECHAT',
              qrAssetId: 'qr-1',
              qrAssetName: '测试收款码',
              requestedAmountMinor: 10000,
              payableAmountMinor: 10000,
              amountOffsetMinor: 0,
              currency: 'CNY',
              status: orderStatus,
              publicToken: 'public-token',
              payUrl: 'https://pay.example.test/pay/public-token',
              confirmationStatus:
                orderStatus === 'PAID' ? 'NOTIFICATION' : 'UNCONFIRMED',
              amountSlotStatus:
                orderStatus === 'PAID' ? 'COOLING' : 'ACTIVE',
              createdAt: '2026-07-26T10:00:00+00:00',
              expiresAt: '2026-07-26T10:05:00+00:00',
              paidAt:
                orderStatus === 'PAID'
                  ? '2026-07-26T10:01:00+00:00'
                  : undefined
            }
          ],
          total: 1
        }
      })
    })
  );
  await page.route(
    '**/payment/orders/order-1/match-candidates',
    route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          data: [
            {
              id: 'event-1',
              clientEventId: 'CLIENT-EVENT-001',
              platform: 'WECHAT',
              amountMinor: 10000,
              currency: 'CNY',
              eventTime: '2026-07-26T10:01:00+00:00',
              receivedAt: '2026-07-26T10:01:01+00:00',
              status: 'RECEIVED',
              duplicateStatus: 'NONE',
              exactMatch: true
            }
          ]
        })
      })
  );
  await page.route('**/payment/orders/order-1/match', async route => {
    submittedMatch = route.request().postDataJSON();
    orderStatus = 'PAID';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          id: 'order-1',
          status: 'PAID',
          matchedEventId: 'event-1'
        }
      })
    });
  });

  await page.goto('/login');
  await page.locator('#login-username').fill('merchant-user');
  await page.locator('#login-password').fill('merchant-password');
  await page.locator('.submit-button').click();

  await expect(page).toHaveURL(/\/index$/);
  const quickAction = page.locator('.quick-grid button').filter({
    hasText: '创建订单'
  });
  await expect(quickAction).toBeVisible();
  await quickAction.click();

  await expect(page).toHaveURL(/\/payment\/payment-operations\/order$/);
  await expect(page.locator('.wscn-http404-container')).toHaveCount(0);
  await expect(page.getByText('动态金额支付订单')).toBeVisible();
  await page.getByRole('button', { name: '补单' }).click();
  const matchDialog = page.getByRole('dialog', { name: '人工补单' });
  await expect(matchDialog).toBeVisible();
  await matchDialog.getByRole('combobox', { name: /支付事件/ }).click();
  await page.locator('.el-select-dropdown__item').filter({
    hasText: 'CLIENT-EVENT-001'
  }).click();
  await expect(page.getByText('本次补单不需要 MFA')).toBeVisible();
  await page.getByRole('button', { name: '确认补单' }).click();
  await expect.poll(() => submittedMatch).toEqual({
    eventId: 'event-1',
    force: false,
    note: ''
  });
  await expect(page.getByText('已支付').first()).toBeVisible();
});

test('platform administrator defaults to all merchants and switches scope without reloading', async ({
  page
}) => {
  const merchantOne = {
    id: 'merchant-1',
    merchantCode: 'M001',
    name: '商户一',
    status: '0',
    lifecycleStatus: 'ACTIVE',
    timezone: 'Asia/Shanghai'
  };
  const merchantTwo = {
    id: 'merchant-2',
    merchantCode: 'M002',
    name: '商户二',
    status: '0',
    lifecycleStatus: 'ACTIVE',
    timezone: 'Asia/Shanghai'
  };
  const listScopeHeaders: Array<string | undefined> = [];

  await page.route('**/auth/login', async route => {
    const envelope = route.request().postDataJSON() as ApiCryptoEnvelope;
    const { masterKey } = decryptRequestEnvelope(envelope);
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': API_CRYPTO_CONTENT_TYPE,
        'X-Api-Crypto-Version': '2'
      },
      body: JSON.stringify(
        encryptResponseEnvelope(envelope, masterKey, {
          code: 200,
          data: {
            access_token: 'platform-admin-access-token',
            mfaRequired: false,
            mfaSetupRequired: false
          }
        })
      )
    });
  });
  await page.route('**/system/user/getInfo', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          user: {
            userId: '1',
            userName: 'admin',
            nickName: '平台管理员',
            avatarUrl: ''
          },
          roles: ['admin'],
          permissions: ['*:*:*']
        }
      })
    })
  );
  await page.route('**/system/menu/getRouters', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: [
          {
            path: '/payment',
            component: 'Layout',
            name: 'Payment',
            meta: { title: '支付管理' },
            children: [
              {
                path: 'platform',
                component: 'ParentView',
                name: 'PaymentPlatform',
                meta: { title: '平台管理' },
                children: [
                  {
                    path: 'merchant',
                    component: 'payment/merchant/index',
                    name: 'PaymentMerchant',
                    meta: { title: '支付商户' }
                  },
                  {
                    path: 'onboarding',
                    component: 'payment/onboarding/index',
                    name: 'PaymentOnboarding',
                    meta: { title: '商户入驻' }
                  }
                ]
              }
            ]
          }
        ]
      })
    })
  );
  await page.route('**/payment/merchant-context', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          superAdmin: true,
          accountType: 'PLATFORM_ADMIN',
          scopeMode: 'ALL',
          canAccessAllMerchants: true,
          displayTimezone: 'Asia/Shanghai'
        }
      })
    })
  );
  await page.route('**/payment/merchants/options**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: [merchantOne, merchantTwo] })
    })
  );
  await page.route('**/payment/merchants/list**', route => {
    const merchantId = route.request().headers()['x-merchant-id'];
    listScopeHeaders.push(merchantId);
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          total: merchantId ? 1 : 2,
          rows: merchantId === 'merchant-2'
            ? [merchantTwo]
            : [merchantOne, merchantTwo]
        }
      })
    });
  });
  await page.route('**/payment/home-dashboard', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          superAdmin: true,
          scopeMode: 'ALL',
          displayTimezone: 'Asia/Shanghai',
          trend: [],
          merchantHealth: []
        }
      })
    })
  );
  await page.route('**/resource/message**', route =>
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: ''
    })
  );
  await page.route('**/resource/message/box', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: [] })
    })
  );

  await page.goto('/login');
  await page.locator('#login-username').fill('admin');
  await page.locator('#login-password').fill('admin-password');
  await page.locator('.submit-button').click();

  await expect(page).toHaveURL(/\/index$/);
  await expect(page.getByText('商户入驻', { exact: true })).toHaveCount(0);
  await page.getByText('支付管理', { exact: true }).click();
  await page.getByText('支付商户', { exact: true }).click();
  await expect(page).toHaveURL(/\/payment\/platform\/merchant$/);
  await expect(page.locator('.wscn-http404-container')).toHaveCount(0);
  await expect(page.locator('.merchant-selector').first()).toContainText('全部商户');
  await expect(page.locator('.el-table__body')).toContainText('商户一');
  await expect(page.locator('.el-table__body')).toContainText('商户二');
  expect(listScopeHeaders).toContain(undefined);

  await page.evaluate(() => {
    (window as Window & { paymentScopeMarker?: string }).paymentScopeMarker =
      'preserved';
  });
  await page.locator('.merchant-selector .el-select').first().click();
  await page.locator('.el-select-dropdown__item').filter({
    hasText: '商户二'
  }).first().click();

  await expect.poll(() => listScopeHeaders.at(-1)).toBe('merchant-2');
  await expect(page.locator('.el-table__body')).not.toContainText('商户一');
  await expect(page.locator('.el-table__body')).toContainText('商户二');
  await expect.poll(() =>
    page.evaluate(
      () =>
        (window as Window & { paymentScopeMarker?: string })
          .paymentScopeMarker
    )
  ).toBe('preserved');
});

test('payment integration route renders four tabs without entering 404', async ({ page }) => {
  await page.route('**/auth/login', async route => {
    const envelope = route.request().postDataJSON() as ApiCryptoEnvelope;
    const { masterKey } = decryptRequestEnvelope(envelope);
    await route.fulfill({
      status: 200,
      headers: {
        'Content-Type': API_CRYPTO_CONTENT_TYPE,
        'X-Api-Crypto-Version': '2'
      },
      body: JSON.stringify(
        encryptResponseEnvelope(envelope, masterKey, {
          code: 200,
          data: {
            access_token: 'integration-route-access-token',
            mfaRequired: false,
            mfaSetupRequired: false
          }
        })
      )
    });
  });
  await page.route('**/system/user/getInfo', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          user: {
            userId: 'user-integration',
            userName: 'integration-owner',
            nickName: '接入应用管理员',
            avatarUrl: ''
          },
          roles: ['merchant_owner'],
          permissions: [
            'payment:integration:list',
            'payment:integration:add',
            'payment:integration:edit',
            'payment:integration:secret',
            'payment:integration:route',
            'payment:external-order:list',
            'payment:protocol-callback:list',
            'payment:protocol-callback:retry'
          ]
        }
      })
    })
  );
  await page.route('**/system/menu/getRouters', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: [
          {
            path: '/payment',
            component: 'Layout',
            name: 'Payment',
            meta: { title: '支付管理' },
            children: [
              {
                path: 'platform-developer',
                component: 'ParentView',
                name: 'PaymentPlatformDeveloper',
                meta: { title: '平台与开发' },
                children: [
                  {
                    path: 'integration',
                    component: 'payment/integration/index',
                    name: 'PaymentIntegration',
                    meta: { title: '支付接入' }
                  }
                ]
              }
            ]
          }
        ]
      })
    })
  );
  await page.route('**/payment/merchant-context', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          superAdmin: false,
          merchantId: 'merchant-integration',
          merchantCode: 'M000009',
          merchantName: '兼容测试商户'
        }
      })
    })
  );
  await page.route('**/payment/integrations**', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: {
          total: 1,
          rows: [
            {
              id: 'integration-1',
              merchantId: 'merchant-integration',
              integrationCode: 'mall_prod',
              integrationName: '商城正式接入',
              protocol: 'EPAY',
              profile: 'EPAY_CLASSIC_V1',
              pid: '1000000001',
              status: '0',
              defaultExpireSeconds: 300,
              notifyMethod: 'GET',
              callbackPolicy: 'NOTIFICATION_MATCHED',
              allowedCallbackHosts: ['merchant.example.com'],
              activeSecretVersion: 1,
              secrets: [],
              createdAt: '2026-07-20T00:00:00Z',
              updatedAt: '2026-07-20T00:00:00Z'
            }
          ]
        }
      })
    })
  );
  await page.route('**/resource/message**', route =>
    route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: ''
    })
  );
  await page.route('**/resource/message/box', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: [] })
    })
  );

  await page.goto('/login?redirect=/payment/platform-developer/integration');
  await page.locator('#login-username').fill('integration-owner');
  await page.locator('#login-password').fill('integration-password');
  await page.locator('.submit-button').click();

  await expect(page).toHaveURL(/\/payment\/platform-developer\/integration$/);
  await expect(page.locator('.wscn-http404-container')).toHaveCount(0);
  await expect(page.getByText('易支付接入应用')).toBeVisible();
  await expect(page.getByRole('button', { name: '商城正式接入', exact: true })).toBeVisible();
  for (const tab of ['接入应用', '支付路由', '外部订单', '回调记录']) {
    await expect(page.getByRole('tab', { name: tab })).toBeVisible();
  }
});


interface ApiCryptoEnvelope {
  v: number;
  kid: string;
  jti: string;
  ts: number;
  wrappedKey: string;
  iv: string;
  ciphertext: string;
  tag: string;
}

function decryptRequestEnvelope(envelope: ApiCryptoEnvelope) {
  const masterKey = privateDecrypt(
    {
      key: apiCryptoKeyPair.privateKey,
      padding: constants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: 'sha256'
    },
    decodeBase64Url(envelope.wrappedKey)
  );
  const aad = Buffer.from(
    `2\nREQUEST\nPOST\n${API_CRYPTO_PATH}\n${API_CRYPTO_KID}\n${envelope.jti}\n${envelope.ts}`,
    'utf8'
  );
  const decipher = createDecipheriv('aes-256-gcm', masterKey, decodeBase64Url(envelope.iv));
  decipher.setAAD(aad);
  decipher.setAuthTag(decodeBase64Url(envelope.tag));
  const plaintext = Buffer.concat([
    decipher.update(decodeBase64Url(envelope.ciphertext)),
    decipher.final()
  ]);
  return {
    masterKey,
    plaintext: JSON.parse(plaintext.toString('utf8')) as {
      username: string;
      password: string;
    }
  };
}

function encryptResponseEnvelope(
  requestEnvelope: ApiCryptoEnvelope,
  masterKey: Buffer,
  plaintext: unknown
) {
  const timestamp = Math.floor(Date.now() / 1000);
  const responseKey = deriveResponseKey(masterKey, requestEnvelope.jti);
  const iv = randomBytes(12);
  const aad = Buffer.from(
    `2\nRESPONSE\n200\n${API_CRYPTO_PATH}\n${requestEnvelope.jti}\n${timestamp}`,
    'utf8'
  );
  const cipher = createCipheriv('aes-256-gcm', responseKey, iv);
  cipher.setAAD(aad);
  const ciphertext = Buffer.concat([
    cipher.update(JSON.stringify(plaintext), 'utf8'),
    cipher.final()
  ]);
  return {
    v: 2,
    jti: requestEnvelope.jti,
    ts: timestamp,
    status: 200,
    iv: encodeBase64Url(iv),
    ciphertext: encodeBase64Url(ciphertext),
    tag: encodeBase64Url(cipher.getAuthTag())
  };
}

function deriveResponseKey(masterKey: Buffer, jti: string) {
  const salt = createHash('sha256').update('payment-monitor/api-crypto-v2', 'utf8').digest();
  const prk = createHmac('sha256', salt).update(masterKey).digest();
  const info = Buffer.from(`response|${jti}|POST|${API_CRYPTO_PATH}`, 'utf8');
  return createHmac('sha256', prk)
    .update(Buffer.concat([info, Buffer.from([1])]))
    .digest();
}

function decodeBase64Url(value: string) {
  return Buffer.from(value, 'base64url');
}

function encodeBase64Url(value: Buffer) {
  return value.toString('base64url');
}
