import { isPathMatch } from '../utils/validate';

export const publicRoutePatterns = [
  '/',
  '/overview',
  '/guide',
  '/guide/*',
  '/login',
  '/forgot-password',
  '/register',
  '/social-callback',
  '/register*',
  '/register/*',
  '/merchant-invitation/*'
] as const;

export const isPublicRoutePath = (path: string) =>
  publicRoutePatterns.some(pattern => isPathMatch(pattern, path));
