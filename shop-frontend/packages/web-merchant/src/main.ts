/**
 * 商家后台应用入口文件
 * 创建Vue实例、注册插件、挂载到页面
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/index.css'

// Element Plus 按需导入样式
// ElMessage、ElMessageBox、ElNotification、ElLoading 这些通过 JS 调用的组件，
// 不会被 unplugin-vue-components 自动导入样式，需要手动引入对应的 CSS
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
