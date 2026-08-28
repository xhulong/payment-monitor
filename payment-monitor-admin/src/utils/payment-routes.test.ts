import { describe, expect, it } from 'vitest';
import {
  filterAuthorizedPaymentItems,
  filterPlatformPaymentRoutes,
  hasPaymentPermission,
  isNavigableRouteMatch,
  PAYMENT_ROUTE_PERMISSIONS,
  PAYMENT_ROUTES
} from './payment-routes';

describe('payment nested routes', () => {
  it('uses the business-group routes created by the V13 menu migration', () => {
    expect(PAYMENT_ROUTES).toEqual({
      onboarding: '/payment/merchant-center/onboarding',
      merchant: '/payment/merchant-center/merchant',
      device: '/payment/payment-operations/device',
      qrcode: '/payment/payment-operations/qrcode',
      order: '/payment/payment-operations/order',
      event: '/payment/payment-operations/event',
      transaction: '/payment/finance-risk/transaction',
      sensitiveOperation: '/payment/finance-risk/sensitive-operation',
      reconciliation: '/payment/finance-risk/reconciliation',
      webhook: '/payment/platform-developer/webhook',
      integration: '/payment/platform-developer/integration'
    });
  });

  it('filters navigation items by their list permission', () => {
    const items = [
      {
        route: PAYMENT_ROUTES.order,
        permission: PAYMENT_ROUTE_PERMISSIONS.order
      },
      {
        route: PAYMENT_ROUTES.device,
        permission: PAYMENT_ROUTE_PERMISSIONS.device
      }
    ];

    expect(
      filterAuthorizedPaymentItems(items, ['payment:order:list'])
    ).toEqual([items[0]]);
    expect(filterAuthorizedPaymentItems(items, ['*:*:*'])).toEqual(items);
    expect(
      hasPaymentPermission(['payment:device:list'], 'payment:order:list')
    ).toBe(false);
  });

  it('rejects the catch-all route before navigation reaches the 404 page', () => {
    expect(isNavigableRouteMatch(['/:pathMatch(.*)*'])).toBe(false);
    expect(
      isNavigableRouteMatch([
        '/payment',
        '/payment/payment-operations',
        '/payment/payment-operations/order'
      ])
    ).toBe(true);
  });

  it('removes only the payment onboarding route for platform accounts', () => {
    const routes = [
      {
        component: 'Layout',
        children: [
          { component: 'payment/onboarding/index' },
          { component: 'payment/merchant/index' }
        ]
      },
      { component: 'system/user/index' }
    ];
    expect(filterPlatformPaymentRoutes(routes, true)).toEqual([
      {
        component: 'Layout',
        children: [{ component: 'payment/merchant/index' }]
      },
      { component: 'system/user/index' }
    ]);
    expect(filterPlatformPaymentRoutes(routes, false)).toBe(routes);
  });
});
