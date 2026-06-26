/// <reference types="vite/client" />

/**
 * 声明 .vue 文件的类型
 * 让 TypeScript 认识 .vue 文件，不然 import xxx.vue 会报错
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

/**
 * 声明环境变量的类型
 * Vite 的环境变量都以 VITE_ 开头，这里声明一下类型方便代码提示
 */
interface ImportMetaEnv {
  /** API基础地址 */
  readonly VITE_API_BASE_URL: string
  /** 应用标题 */
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
