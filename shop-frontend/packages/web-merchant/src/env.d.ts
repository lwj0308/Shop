/// <reference types="vite/client" />

/**
 * 声明.vue模块类型
 * 让TypeScript认识.vue文件，不然import的时候会报错
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
