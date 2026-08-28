import axiosModule from 'axios';
import { setToken } from '@/utils/auth';

const axios = axiosModule as any;
let refreshPromise: Promise<string | null> | null = null;

export function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }
  refreshPromise = axios
    .post(
      `${import.meta.env.VITE_APP_BASE_API}/auth/refresh`,
      {},
      {
        withCredentials: true,
        headers: {
          clientid: import.meta.env.VITE_APP_CLIENT_ID
        }
      }
    )
    .then((response: any) => {
      const token = response?.data?.data?.access_token;
      if (!token) {
        return null;
      }
      setToken(token);
      return token as string;
    })
    .catch(() => null)
    .finally(() => {
      refreshPromise = null;
    });
  return refreshPromise;
}
