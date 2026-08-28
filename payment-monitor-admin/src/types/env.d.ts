declare module '*.vue' {
  import { DefineComponent } from 'vue';
  const Component: DefineComponent<{}, {}, any>;
  export default Component;
}

declare module 'virtual:svg-icons-register' {}
declare module 'virtual:*' {}

// 环境变量
interface ImportMetaEnv {
  VITE_APP_TITLE: string;
  VITE_APP_PORT: number;
  VITE_APP_BASE_API: string;
  VITE_APP_PROXY_TARGET?: string;
  VITE_APP_BASE_URL: string;
  VITE_APP_CONTEXT_PATH: string;
  VITE_APP_MONITOR_ADMIN: string;
  VITE_APP_SNAILJOB_ADMIN: string;
  VITE_APP_ENV: string;
  VITE_APP_CLIENT_ID: string;
  VITE_APP_API_CRYPTO_V2: string;
  VITE_APP_MESSAGE_ENABLED: string;
  VITE_APP_MESSAGE_TRANSPORT: string;
  VITE_APP_MESSAGE_PATH: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
  // readonly glob: any;
}
