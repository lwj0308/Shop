/**
 * 应用入口文件
 * 这里是整个用户端应用的启动点
 * 主要做三件事：创建Vue实例、注册插件（路由/状态管理）、挂载到页面
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.css'

/**
 * Element Plus 指令式API的样式（按需导入模式下需手动引入）
 * 组件（如el-button）的样式由 unplugin-vue-components 自动导入
 * 但 ElMessage、ElMessageBox 等指令式API的样式需要手动引入
 */
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

// 创建Vue应用实例
const app = createApp(App)

// 注册Pinia状态管理（类似Vuex，但是更好用）
app.use(createPinia())

// 注册Vue Router路由
app.use(router)

// 把应用挂载到 index.html 里的 <div id="app"></div>
app.mount('#app')
