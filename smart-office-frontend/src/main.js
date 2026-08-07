import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
// 图标按需注册：仅全局注册模板中直接使用的图标，其余由各页面局部 import
import {
  ArrowDown, Bell, Calendar, Collection, DataAnalysis, Document,
  List, MagicStick, Message, Notebook, Odometer, Present, Setting, TrendCharts
} from '@element-plus/icons-vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import './styles/main.css'

const app = createApp(App)

// 注册实际用到的 Element Plus 图标（按需，替代原先全量注册）
;[
  ArrowDown, Bell, Calendar, Collection, DataAnalysis, Document,
  List, MagicStick, Message, Notebook, Odometer, Present, Setting, TrendCharts
].forEach((icon) => app.component(icon.name, icon))

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
