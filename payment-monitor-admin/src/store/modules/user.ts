import { to } from 'await-to-js';
import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { UserInfo } from '@/api/system/user/types';
import type { LoginData, LoginResult } from '@/api/types';
import type { RuoYiAjaxResult } from '@/utils/api-types';
import {
  getInfo as getUserInfo,
  login as loginApi,
  logout as logoutApi,
  verifyMfaLogin
} from '@/api/login';
import defAva from '@/assets/images/profile.jpg';
import { getToken, removeToken, setToken } from '@/utils/auth';
import { useMerchantStore } from '@/store/modules/merchant';

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken());
  const name = ref('');
  const nickname = ref('');
  const userId = ref<string | number>('');
  const avatar = ref('');
  const roles = ref<Array<string>>([]); // 用户角色编码集合 → 判断路由权限
  const permissions = ref<Array<string>>([]); // 用户权限编码集合 → 判断按钮权限

  /**
   * 登录
   * @param userInfo
   * @returns
   */
  const applyLoginResult = (data: LoginResult) => {
    if (data.access_token) {
      setToken(data.access_token);
      token.value = data.access_token;
    }
  };

  const login = async (userInfo: LoginData): Promise<LoginResult> => {
    const [err, res] = await to(loginApi(userInfo));
    if (res) {
      const data = (res as RuoYiAjaxResult<LoginResult>).data;
      if (!data) {
        return Promise.reject(new Error('登录响应缺少数据'));
      }
      applyLoginResult(data);
      if (!data.access_token && !data.mfaChallengeToken) {
        return Promise.reject(new Error('登录响应缺少会话信息'));
      }
      return data;
    }
    return Promise.reject(err || new Error('登录失败'));
  };

  const verifyMfa = async (challengeToken: string, code: string): Promise<LoginResult> => {
    const [err, res] = await to(verifyMfaLogin({ challengeToken, code }));
    if (res) {
      const data = (res as RuoYiAjaxResult<LoginResult>).data;
      if (!data?.access_token) {
        return Promise.reject(new Error('MFA 验证响应缺少访问令牌'));
      }
      applyLoginResult(data);
      return data;
    }
    return Promise.reject(err || new Error('MFA 验证失败'));
  };

  // 获取用户信息
  const getInfo = async (): Promise<void> => {
    const [err, res] = await to(getUserInfo());
    if (res) {
      const data = (res as RuoYiAjaxResult<UserInfo>).data;
      if (!data?.user) {
        return Promise.reject(err);
      }
      const user = data.user;
      const profile = user.avatarUrl == '' || user.avatarUrl == null ? defAva : user.avatarUrl;

      if (data.roles && data.roles.length > 0) {
        // 验证返回的roles是否是一个非空数组
        roles.value = data.roles;
        permissions.value = data.permissions;
      } else {
        roles.value = ['ROLE_DEFAULT'];
      }
      name.value = user.userName;
      nickname.value = user.nickName;
      avatar.value = profile;
      userId.value = user.userId;
      return Promise.resolve();
    }
    return Promise.reject(err);
  };

  // 注销
  const logout = async (): Promise<void> => {
    await logoutApi();
    token.value = '';
    roles.value = [];
    permissions.value = [];
    useMerchantStore().clear();
    removeToken();
  };

  const setAvatar = (value: string) => {
    avatar.value = value;
  };

  return {
    userId,
    token,
    nickname,
    avatar,
    roles,
    permissions,
    login,
    verifyMfa,
    getInfo,
    logout,
    setAvatar
  };
});
