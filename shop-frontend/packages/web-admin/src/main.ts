/**
 * 管理后台应用入口文件
 * 创建Vue实例、注册插件、挂载到页面
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setupPermissionDirective } from './directives/permission'
import './styles/index.css'

// Element Plus 暗黑模式CSS变量（必须导入才能支持暗黑主题切换）
import 'element-plus/theme-chalk/dark/css-vars.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 注册全局权限指令 v-permission
setupPermissionDirective(app)

app.mount('#app')
