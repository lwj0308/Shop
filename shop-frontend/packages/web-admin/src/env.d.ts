/// <reference types="vite/client" />

/**
 * 环境变量类型声明
 * 让TypeScript认识import.meta.env中的自定义变量
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
