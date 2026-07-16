/// <reference types="vite/client" />

/**
 * 声明 Vite 环境变量的类型
 * shared 包被各 web-* 应用通过路径别名引用，但编辑器/tsserver 在单独解析
 * shared 源码时不会加载各应用的 env.d.ts，因此这里单独声明一份，
 * 保证 import.meta.env 在 shared 内部也有正确类型提示。
 * Vite 的环境变量都以 VITE_ 开头。
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
