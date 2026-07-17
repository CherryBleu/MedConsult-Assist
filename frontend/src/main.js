import { createApp } from 'vue'
import App from './App.vue'
import pinia from './store'
import router from './router'
import {
  ElAlert,
  ElAside,
  ElAvatar,
  ElBadge,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElPopover,
  ElProgress,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElRate,
  ElRow,
  ElSelect,
  ElSubMenu,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTimePicker,
  ElUpload
} from 'element-plus'
import 'element-plus/dist/index.css'
// 仅导入当前模板和动态菜单实际引用的图标，避免把完整图标库打入 vendor。
import {
  ArrowDown,
  ArrowLeft,
  Bell,
  Box,
  Calendar,
  CircleCheck,
  Clock,
  Close,
  Cpu,
  Document,
  Expand,
  FirstAidKit,
  Fold,
  House,
  InfoFilled,
  List,
  Loading,
  Lock,
  Monitor,
  Phone,
  PictureFilled,
  Plus,
  Search,
  Setting,
  Suitcase,
  UploadFilled,
  User,
  UserFilled,
  Wallet,
  Warning
} from '@element-plus/icons-vue'
// 全局样式
import './styles/global.css'

const app = createApp(App)

const elementPlusComponents = [
  ElAlert,
  ElAside,
  ElAvatar,
  ElBadge,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElPopover,
  ElProgress,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElRate,
  ElRow,
  ElSelect,
  ElSubMenu,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTimePicker,
  ElUpload
]

const icons = {
  ArrowDown,
  ArrowLeft,
  Bell,
  Box,
  Calendar,
  CircleCheck,
  Clock,
  Close,
  Cpu,
  Document,
  Expand,
  FirstAidKit,
  Fold,
  House,
  InfoFilled,
  List,
  Loading,
  Lock,
  Monitor,
  Phone,
  PictureFilled,
  Plus,
  Search,
  Setting,
  Suitcase,
  UploadFilled,
  User,
  UserFilled,
  Wallet,
  Warning
}

for (const component of elementPlusComponents) {
  app.use(component)
}

app.use(ElLoading)

for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.mount('#app')
