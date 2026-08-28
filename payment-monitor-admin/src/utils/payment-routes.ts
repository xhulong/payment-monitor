export const PAYMENT_ROUTES = {
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
} as const;

export type PaymentRouteKey = keyof typeof PAYMENT_ROUTES;

export const PAYMENT_ROUTE_PERMISSIONS: Record<PaymentRouteKey, string> = {
  onboarding: 'payment:onboarding:view',
  merchant: 'payment:merchant:list',
  device: 'payment:device:list',
  qrcode: 'payment:qrcode:list',
  order: 'payment:order:list',
  event: 'payment:event:list',
  transaction: 'payment:transaction:list',
  sensitiveOperation: 'payment:sensitive-operation:list',
  reconciliation: 'payment:reconciliation:list',
  webhook: 'payment:webhook:list',
  integration: 'payment:integration:list'
};

export interface PaymentNavigationItem {
  route: string;
  permission: string;
}

export const hasPaymentPermission = (
  permissions: string[],
  permission: string
) =>
  permissions.includes('*:*:*') ||
  permissions.includes(permission);

export const filterAuthorizedPaymentItems = <
  T extends PaymentNavigationItem
>(
  items: T[],
  permissions: string[]
) =>
  items.filter(item => hasPaymentPermission(permissions, item.permission));

export const isNavigableRouteMatch = (matchedPaths: string[]) =>
  matchedPaths.some(path => !path.includes(':pathMatch'));

interface PaymentMenuRoute {
  component?: unknown;
  children?: PaymentMenuRoute[];
}

export const filterPlatformPaymentRoutes = <T extends PaymentMenuRoute>(
  routes: T[],
  platformAccount: boolean
): T[] => {
  if (!platformAccount) return routes;
  return routes
    .filter(route => String(route.component || '') !== 'payment/onboarding/index')
    .map(route => ({
      ...route,
      children: route.children
        ? filterPlatformPaymentRoutes(route.children, true)
        : route.children
    })) as T[];
};
