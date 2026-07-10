# 智慧医疗系统前端

基于 Vue3 + Vite + Element Plus + Pinia 的智慧医疗系统前端，支持患者、医生、医院管理员、药房管理员四种角色。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | ^3.2.8 | 前端框架，使用 Composition API (`<script setup>`) |
| Vite | ^2.5.2 | 构建工具 |
| Vue Router | ^4.6.4 | 路由管理，含角色权限守卫 |
| Pinia | ^3.0.4 | 状态管理 |
| Element Plus | ^2.14.2 | UI 组件库 |
| @element-plus/icons-vue | ^2.3.2 | 图标库（全局注册） |
| Axios | ^1.18.1 | HTTP 请求库，含拦截器与 Token 自动刷新 |
| dayjs | ^1.11.21 | 日期处理 |

## 快速开始

### 环境要求

- Node.js >= 14
- npm >= 6

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

默认端口为 `3000`，若被占用 Vite 会自动递增（3001、3002...）。

如需固定端口，在 `vite.config.mjs` 的 `server` 中添加 `port` 配置：

```js
server: {
  port: 5173,
  proxy: { ... }
}
```

### 生产构建

```bash
npm run build
```

构建产物输出到 `dist/` 目录。

## 演示账号

系统内置 4 个演示账号，密码任意 6 位以上即可（Mock 模式下）：

| 角色 | 账号 | 姓名 | 说明 |
|------|------|------|------|
| 患者 | `patient` | 测试患者 | 预约挂号、病历查看、AI 问诊、分诊、影像检测 |
| 医生 | `doctor` | 张医生（主任医师） | 工作台、接诊管理、病历书写、AI 工具 |
| 医院管理员 | `admin` | 系统管理员 | 用户/患者/医生/科室管理、排班、药品库存、AI 管理 |
| 药房管理员 | `pharmacy` | 药房管理员 | 工作台、药品目录、库存管理、库存预警、流水 |

> 登录页底部有演示账号快捷入口，点击即可自动填充账号密码。

## 项目结构

```
src/
├── api/              # API 接口层（12个模块，每个含Mock开关）
├── assets/           # 静态资源
├── constants/        # 常量（角色枚举、状态枚举）
├── layouts/          # 布局组件（MainLayout：侧边栏 + 顶栏 + 主区域）
├── mock/             # Mock 数据（10个模块，按角色生成）
├── router/           # 路由配置
│   ├── index.js      # 路由实例 + 全局守卫
│   └── modules/      # 按角色拆分的路由模块
│       ├── common.js
│       ├── patient.js
│       ├── doctor.js
│       ├── admin.js
│       └── pharmacy.js
├── store/            # Pinia 状态管理
│   ├── index.js
│   └── modules/
│       ├── user.js   # 用户/登录态
│       ├── notice.js # 通知
│       └── aiChat.js # AI 对话
├── styles/           # 全局样式（CSS变量、重置、全局）
├── utils/            # 工具函数
│   ├── auth.js       # Token 存取（localStorage）
│   └── request.js    # Axios 封装（拦截器、Token 自动刷新）
├── views/            # 页面组件（按角色分目录）
│   ├── common/       # 登录、注册、首页跳转、404、通知列表
│   ├── patient/      # 患者端（13个页面）
│   ├── doctor/       # 医生端（8个页面）
│   ├── admin/        # 管理员端（10个页面）
│   └── pharmacy/     # 药房端（5个页面）
├── App.vue
└── main.js           # 入口文件
```

## Mock 数据模式切换

项目默认开启 Mock 模式，所有接口返回本地模拟数据，无需后端即可完整演示。

### 切换到真实后端接口

编辑 `.env.development`：

```env
# 将 true 改为 false 即切换到真实后端
VITE_USE_MOCK=false
```

修改后**重启开发服务器**生效。

### API 接口约定

所有真实接口统一遵循以下规范：

- **基础路径**：`/api/v1/...`
- **请求方法**：RESTful 风格（GET/POST/PUT/DELETE）
- **认证方式**：Request Header `Authorization: Bearer <accessToken>`
- **响应格式**：
  ```json
  {
    "code": 0,
    "message": "success",
    "data": { ... }
  }
  ```
  - `code = 0`：请求成功
  - `code = 401`：Token 过期，前端会自动使用 refreshToken 刷新后重试
  - 其他 code：请求失败，前端弹出 `message` 提示
- **分页格式**：列表接口 `data` 结构为：
  ```json
  {
    "records": [],
    "total": 100,
    "pageNum": 1,
    "pageSize": 10
  }
  ```

### Vite 代理配置

开发环境下，`/api` 前缀的请求会被代理到 `http://localhost:8080`（后端服务地址）。若后端端口不同，修改 `vite.config.mjs`：

```js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:你的后端端口',
      changeOrigin: true
    }
  }
}
```

### 生产环境配置

编辑 `.env.production`：

```env
VITE_API_BASE_URL = 'https://你的线上后端域名/api'
```

## 功能模块

### 患者端
- 首页（科室导航、快捷功能）
- 预约挂号：选择科室 → 选择医生 → 选择排班 → 确认预约
- 我的预约：查看/取消预约、支付挂号费、签到
- 我的病历：病历列表、病历详情
- 个人中心：个人信息、健康档案
- AI 服务：智能分诊、AI 问诊（症状自诊）、影像检测

### 医生端
- 工作台：今日待就诊统计、快捷功能
- 接诊管理：待接诊列表、开始接诊、结束接诊、标记爽约
- 病历管理：病历列表、书写病历、病历详情
- 我的排班：查看个人排班
- AI 工具：病历摘要、用药分析、影像辅助

### 管理员端
- 系统管理：用户管理、患者管理、医生管理、科室管理
- 排班管理：创建/编辑/停用排班
- 药品库存：药品管理、库存管理、库存预警
- AI 管理：调用日志、反馈管理

### 药房端
- 工作台：待处理统计
- 药品库存：药品目录、库存管理（入库/出库）、库存预警、库存流水

## API 接口清单

### 认证模块（5个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| registerApi | POST | `/auth/register` | 用户注册 |
| loginApi | POST | `/auth/login` | 登录（返回 accessToken + refreshToken） |
| getUserInfoApi | GET | `/auth/me` | 获取当前用户信息 |
| refreshTokenApi | POST | `/auth/refresh-token` | 刷新 Token |
| logoutApi | POST | `/auth/logout` | 登出 |

### 科室与医生（4个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getDepartmentListApi | GET | `/department/list` | 科室列表 |
| getDoctorListApi | GET | `/doctor/list?departmentId=` | 按科室查询医生 |
| getDoctorDetailApi | GET | `/doctor/{id}` | 医生详情 |
| getDoctorListApi (系统) | GET | `/system/doctor/list` | 管理员-医生列表 |

### 预约与排班（17个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getScheduleListApi | GET | `/doctors/{doctorId}/schedules` | 医生排班 |
| getAppointmentListApi | GET | `/appointments/my` | 我的预约 |
| getAppointmentDetailApi | GET | `/appointments/{id}` | 预约详情 |
| createAppointmentApi | POST | `/appointments` | 创建预约 |
| cancelAppointmentApi | POST | `/appointments/{id}/cancel` | 取消预约 |
| payAppointmentApi | POST | `/appointments/{id}/pay` | 支付挂号费 |
| checkInAppointmentApi | POST | `/appointments/{id}/checkin` | 签到 |
| markNoShowApi | POST | `/appointments/{id}/no-show` | 标记爽约 |
| getReceptionListApi | GET | `/appointments/reception` | 医生接诊列表 |
| startVisitApi | POST | `/appointments/{id}/start` | 开始接诊 |
| endVisitApi | POST | `/appointments/{id}/complete` | 结束接诊 |
| getAdminAppointmentListApi | GET | `/admin/appointments` | 管理员预约列表 |
| getScheduleManageListApi | GET | `/admin/schedules` | 排班管理列表 |
| createScheduleApi | POST | `/admin/schedules` | 创建排班 |
| updateScheduleApi | PUT | `/admin/schedules/{id}` | 更新排班 |
| deleteScheduleApi | DELETE | `/admin/schedules/{id}` | 删除排班 |
| toggleScheduleStatusApi | PUT | `/admin/schedules/{id}/status` | 启用/停用排班 |

### 病历（5个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getRecordListApi | GET | `/medical-record/page` | 病历分页列表 |
| getRecordDetailApi | GET | `/medical-record/{id}` | 病历详情 |
| createRecordApi | POST | `/medical-record` | 创建病历 |
| updateRecordApi | PUT | `/medical-record/{id}` | 更新病历（草稿） |
| archiveRecordApi | POST | `/medical-record/{id}/archive` | 归档病历 |

### 药品与库存（7个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getDrugListApi | GET | `/drug/list` | 药品列表 |
| addDrugApi | POST | `/drug/add` | 新增药品 |
| getStockListApi | GET | `/drug/stock/list` | 库存列表 |
| stockInApi | POST | `/drug/stock/in/{id}` | 入库 |
| stockOutApi | POST | `/drug/stock/out/{id}` | 出库 |
| getStockWarningApi | GET | `/drug/stock/warning` | 库存预警 |
| getStockFlowApi | GET | `/drug/stock-flow` | 库存流水 |

### 通知（5个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getNoticeListApi | GET | `/notices` | 通知列表（分页） |
| getUnreadCountApi | GET | `/notices/unread-count` | 未读数 |
| markReadApi | POST | `/notices/{id}/read` | 标记已读 |
| markAllReadApi | POST | `/notices/read-all` | 全部已读 |
| deleteNoticeApi | DELETE | `/notices/{id}` | 删除通知 |

### AI 服务（12个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| triageApi | POST | `/ai/triage` | 智能分诊 |
| createSessionApi | POST | `/ai/symptom-chat/session` | 创建问诊会话 |
| sendChatMessageApi | POST | `/ai/symptom-chat` | 发送问诊消息 |
| getSessionHistoryApi | GET | `/ai/symptom-chat/history/{sid}` | 会话历史 |
| generateSummaryByRecordApi | POST | `/ai/summary/by-record/{recordNo}` | 按病历生成摘要 |
| generateSummaryByTextApi | POST | `/ai/summary/by-text` | 按文本生成摘要 |
| confirmSummaryApi | PUT | `/ai/summary/{id}/confirm` | 医生确认摘要 |
| medicationAnalysisApi | POST | `/ai/medication-analysis` | 用药分析 |
| submitImagingDetectionApi | POST | `/ai/imaging-detection/submit` | 提交影像检测 |
| getImagingResultApi | GET | `/ai/imaging-detection/{taskId}` | 查询影像结果 |
| reviewImagingDetectionApi | PUT | `/ai/imaging-detection/{taskId}/review` | 医生复核影像 |
| getImagingHistoryListApi | GET | `/ai/imaging-detection/list` | 影像历史列表 |

### 系统管理（10个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getUserListApi | GET | `/system/user/list` | 用户列表 |
| addUserApi | POST | `/system/user/add` | 新增用户 |
| updateUserApi | PUT | `/system/user/update/{id}` | 更新用户 |
| deleteUserApi | DELETE | `/system/user/delete/{id}` | 删除用户 |
| getDeptListApi | GET | `/system/department/list` | 科室列表 |
| addDeptApi | POST | `/system/department/add` | 新增科室 |
| updateDeptApi | PUT | `/system/department/update/{id}` | 更新科室 |
| deleteDeptApi | DELETE | `/system/department/delete/{id}` | 删除科室 |
| addDoctorApi | POST | `/system/doctor/add` | 新增医生 |
| updateDoctorApi | PUT | `/system/doctor/update/{id}` | 更新医生 |
| deleteDoctorApi | DELETE | `/system/doctor/delete/{id}` | 删除医生 |

### 患者管理（7个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getPatientInfoApi | GET | `/patient/info` | 患者个人信息 |
| getHealthArchiveApi | GET | `/patient/health-archive` | 健康档案 |
| updatePatientInfoApi | PUT | `/patient/update` | 更新患者信息 |
| getAdminPatientListApi | GET | `/admin/patient/list` | 管理员-患者列表 |
| getPatientDetailApi | GET | `/admin/patient/detail/{id}` | 患者详情 |
| updatePatientStatusApi | PUT | `/admin/patient/status/{id}` | 启用/停用患者 |
| addPatientApi | POST | `/admin/patient/add` | 新增患者 |

### AI 管理（4个）

| 函数名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| getAiCallLogApi | GET | `/ai/call-log` | AI 调用日志 |
| getAiFeedbackApi | GET | `/ai/feedback` | AI 反馈列表 |
| submitAiFeedbackApi | POST | `/ai/feedback` | 提交反馈 |
| processFeedbackApi | POST | `/ai/feedback/{id}/reply` | 处理反馈 |

## 用户角色枚举

| 枚举值 | 中文名称 |
|--------|---------|
| `PATIENT` | 患者 |
| `DOCTOR` | 医生 |
| `HOSPITAL_ADMIN` | 医院管理员 |
| `PHARMACY_ADMIN` | 药房管理员 |

## Token 机制

- 登录成功后，后端返回 `accessToken`（有效期2小时）和 `refreshToken`（有效期7天）
- 前端在每次请求 Header 中携带 `Authorization: Bearer <accessToken>`
- 当 `accessToken` 过期（接口返回 401），前端自动使用 `refreshToken` 调用刷新接口获取新 Token
- 若 `refreshToken` 也过期，则强制跳转登录页
- Token 存储在 `localStorage` 中（key：`hospital_token`、`hospital_refresh_token`）
