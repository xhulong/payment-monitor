import { to as tos } from 'await-to-js';
import { ElMessage } from 'element-plus/es';
import * as NProgressModule from 'nprogress';
import 'nprogress/nprogress.css';
import { usePermissionStore } from '@/store/modules/permission';
import { useSettingsStore } from '@/store/modules/settings';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { normalizeInternalRedirect } from '@/utils/navigation';
import { isHandledRequestError, isRelogin } from '@/utils/request';
import { refreshAccessToken } from '@/utils/session';
import { isHttp } from '@/utils/validate';
import router from './router';
import { isPublicRoutePath } from './router/public-routes';

const NProgress = ('default' in NProgressModule ? NProgressModule.default : NProgressModule) as typeof NProgressModule;

NProgress.configure({ showSpinner: false });

let refreshBootstrapAttempted = false;

router.beforeEach(async (to, from) => {
  NProgress.start();
  if (!getToken() && !refreshBootstrapAttempted) {
    refreshBootstrapAttempted = true;
    await refreshAccessToken();
  }
  if (getToken()) {
    const userStore = useUserStore();
    if (isPublicRoutePath(to.path) && to.path !== '/login') {
      return true;
    }
    to.meta.title && useSettingsStore().setTitle(to.meta.title as string);
    /* has token*/
    if (to.path === '/login') {
      NProgress.done();
      return { path: '/index' };
    } else if (isPublicRoutePath(to.path)) {
      return true;
    } else {
      if (userStore.roles.length === 0) {
        isRelogin.show = true;
        // 判断当前用户是否已拉取完user_info信息
        const [err] = await tos(userStore.getInfo());
        if (err) {
          await userStore.logout();
          if (!isHandledRequestError(err)) {
            ElMessage.error(err instanceof Error ? err.message : String(err));
          }
          return { path: '/' };
        } else {
          isRelogin.show = false;
          const accessRoutes = await usePermissionStore().generateRoutes();
          // 根据roles权限生成可访问的路由表
          accessRoutes.forEach(route => {
            if (!isHttp(route.path)) {
              router.addRoute(route); // 动态添加可访问路由表
            }
          });
          // hack方法 确保addRoutes已完成
          return {
            path: to.path,
            replace: true,
            params: to.params,
            query: to.query,
            hash: to.hash,
            name: to.name as string
          };
        }
      } else {
        return true;
      }
    }
  } else {
    // 没有token
    if (isPublicRoutePath(to.path)) {
      // 在免登录白名单，直接进入
      return true;
    } else {
      const redirect = normalizeInternalRedirect(to.fullPath);
      NProgress.done();
      return { path: '/login', query: { redirect } }; // 否则全部重定向到登录页
    }
  }
});

router.afterEach(() => {
  NProgress.done();
});
