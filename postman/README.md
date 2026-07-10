# MedConsult-Assist Postman API 测试集合

本目录提供后端 API 的 Postman 测试集合，方便手动测试与联调。

## 文件清单

| 文件 | 说明 |
|---|---|
| `MedConsult-Assist.postman_collection.json` | API 集合（含接口定义、示例响应、自动脚本） |
| `MedConsult-Assist.postman_environment.json` | 本地环境变量（baseUrl、token 占位） |

## 导入步骤

1. 打开 Postman → Import → 拖入两个 JSON 文件
2. 右上角环境选择器选「MedConsult-Assist 本地」
3. 依次执行接口（见下方流程）

## 已覆盖接口

### Auth 认证服务（接口文档 §2.1）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | 用户注册 | 白名单 |
| 2 | POST | `/api/v1/auth/login` | 用户登录（**自动保存 token**） | 白名单 |
| 3 | GET | `/api/v1/auth/me` | 当前用户信息（手机号脱敏） | 需登录 |
| 4 | POST | `/api/v1/auth/refresh` | 刷新 Token | 白名单 |
| 5 | POST | `/api/v1/auth/logout` | 退出登录 | 白名单 |

### Patient 患者档案服务（接口文档 §2.2）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/patients` | 创建患者档案（**自动保存 patientId**） | 需登录 |
| 2 | GET | `/api/v1/patients/{patientId}` | 查询详情（idNo/phone 脱敏） | 需登录 |
| 3 | GET | `/api/v1/patients?page=&pageSize=&keyword=` | 分页查询 | 需登录 |
| 4 | PUT | `/api/v1/patients/{patientId}` | 更新档案 | 需登录 |
| 5 | PATCH | `/api/v1/patients/{patientId}/status` | 更新状态（ACTIVE/DISABLED/MERGED） | 需登录 |

### Outpatient 门诊服务（接口文档 §2.3-§2.5）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/departments` | 科室列表 | 需登录 |
| 2 | GET | `/api/v1/doctors` | 医生列表（可按 departmentId 过滤） | 需登录 |
| 3 | POST | `/api/v1/schedules` | 创建排班（**自动保存 scheduleId**） | 需登录 |
| 4 | GET | `/api/v1/schedules` | 排班列表（可按科室/日期过滤） | 需登录 |
| 5 | GET | `/api/v1/schedules/available` | 可预约号源 | 需登录 |
| 6 | PATCH | `/api/v1/schedules/{id}/status` | 排班停诊/恢复 | 需登录 |
| 7 | POST | `/api/v1/appointments` | 创建预约/抢号（**自动保存 appointmentId**） | 需登录 |
| 8 | GET | `/api/v1/appointments/{id}` | 预约详情 | 需登录 |
| 9 | GET | `/api/v1/appointments` | 预约列表 | 需登录 |
| 10 | POST | `/api/v1/appointments/{id}/cancel` | 取消预约（释放号源） | 需登录 |
| 11 | PATCH | `/api/v1/appointments/{id}/payment` | 更新支付状态 | 需登录 |
| 12 | PATCH | `/api/v1/appointments/{id}/status` | 就诊状态流转 | 需登录 |

> **抢号并发**：预约创建用 Redis 分布式锁（`lock:schedule:{id}`）防超卖，架构文档 §7.1。
> **状态机**：appointment `BOOKED→CHECKED_IN→IN_PROGRESS→COMPLETED`（或→NO_SHOW/CANCELLED）。

### Drug 药品库存服务（接口文档 §2.7）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/drugs` | 创建药品（**自动保存 drugNo**） | 需登录 |
| 2 | GET | `/api/v1/drugs?keyword=` | 药品列表（模糊搜通用名/商品名） | 需登录 |
| 3 | POST | `/api/v1/drugs/{drugNo}/stock/inbound` | 入库（FEFO 批次累加） | 需登录 |
| 4 | POST | `/api/v1/drugs/{drugNo}/stock/outbound` | 出库（FEFO 近效期优先） | 需登录 |
| 5 | GET | `/api/v1/drugs/{drugNo}/stock/flows` | 库存流水 | 需登录 |
| 6 | GET | `/api/v1/drugs/stock/alerts` | 库存预警（LOW_STOCK / NEAR_EXPIRY） | 需登录 |

### MedicalRecord 电子病历服务（接口文档 §2.6）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/medical-records` | 创建病历（**自动保存 recordId**） | 需登录（DOCTOR） |
| 2 | GET | `/api/v1/medical-records/{recordId}` | 病历详情（PATIENT IDOR SELF） | 需登录 |
| 3 | GET | `/api/v1/medical-records?patientId=` | 病历列表 | 需登录 |
| 4 | PUT | `/api/v1/medical-records/{recordId}` | 更新草稿（仅 DRAFT 可改） | 需登录（DOCTOR） |
| 5 | POST | `/api/v1/medical-records/{recordId}/archive` | 归档（DRAFT→ARCHIVED 不可逆） | 需登录（DOCTOR） |

### Prescription 处方（修改建议 §2.1，8 态状态机）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/prescriptions` | 开方（**自动保存 prescriptionId**） | 需登录（DOCTOR） |
| 2 | GET | `/api/v1/prescriptions?status=` | 处方列表 | 需登录 |
| 3 | GET | `/api/v1/prescriptions/{prescriptionId}` | 处方详情（含明细） | 需登录 |
| 4 | POST | `/api/v1/prescriptions/{id}/submit` | 提交审方（DRAFT→PENDING_REVIEW） | 需登录（DOCTOR） |
| 5 | POST | `/api/v1/prescriptions/{id}/review` | 审方（→APPROVED/REJECTED） | 需登录（PHARMACY_ADMIN） |
| 6 | POST | `/api/v1/prescriptions/{id}/pay` | 缴费（APPROVED→PAID） | 需登录 |
| 7 | POST | `/api/v1/prescriptions/{id}/dispense` | 调剂发药（→DISPENSED，FEFO 同步出库） | 需登录（PHARMACY_ADMIN） |
| 8 | POST | `/api/v1/prescriptions/{id}/complete` | 完成（DISPENSED→COMPLETED） | 需登录 |
| 9 | POST | `/api/v1/prescriptions/{id}/cancel` | 退方（→CANCELLED） | 需登录 |

> **状态机**：DRAFT→PENDING_REVIEW→APPROVED→PAID→DISPENSED→COMPLETED（或 REJECTED/CANCELLED）。
> **调剂补偿**：dispense 多明细逐条 Feign 出库，失败自动调 drug-service rollback-outbound 补回。

### Notification 通知服务（接口文档 §2.8）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/notifications` | 创建通知 | 需登录（管理员） |
| 2 | GET | `/api/v1/notifications?receiverId=&read=` | 通知列表 | 需登录 |
| 3 | PATCH | `/api/v1/notifications/{id}/read` | 标记已读（幂等） | 需登录 |

### AuditLog 审计日志（接口文档 §4.1）

| # | 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/audit-logs` | 多条件查询审计日志 | 需登录（管理员） |

> **集合共 46 接口**（8 文件夹），覆盖全部 6 业务服务的对外 /api/v1/* 接口。

### 待加入（后续服务实现后同步）

- ai-service（§3）：症状自诊/分诊/摘要/用药/影像

## 自动脚本说明

集合内置 JavaScript 脚本（Post-response/Test），自动化处理 token 流转：

- **登录成功** → 自动提取 `accessToken` / `refreshToken` 写入 collection 变量
- **刷新成功** → 自动更新 `accessToken`
- **登出成功** → 自动清空 `accessToken`

后续所有「需登录」接口自动从 collection 级 auth 带 `Authorization: Bearer {{accessToken}}`，无需手动复制。

## 典型测试流程

```
# Auth 流程
1. 注册（POST /auth/register）→ 拿到 userId
2. 登录（POST /auth/login）→ 自动保存 token
3. 当前用户（GET /auth/me）→ 验证 token 生效 + 手机号脱敏

# Patient 流程（依赖 Auth 登录拿到的 token）
4. 创建患者（POST /patients）→ 自动保存 patientId
5. 查询详情（GET /patients/{patientId}）→ 验证 idNo/phone 脱敏
6. 分页查询（GET /patients?keyword=张）
7. 更新档案（PUT /patients/{patientId}）
8. 更新状态（PATCH /patients/{patientId}/status → DISABLED）

# 收尾
9. 登出（POST /auth/logout）→ 清空 token
10. 再调 /me → 应返回 401（token 已清空）
```

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Gateway 地址（本地冒烟） |
| `accessToken` | （空） | 登录后自动填充 |
| `refreshToken` | （空） | 登录后自动填充 |
| `patientId` | （空） | 患者编号 patient_no |
| `doctorId` | `1` | 医生编号 |
| `departmentId` | `1` | 科室编号 |
| `scheduleId` | （空） | 排班编号 schedule_no |
| `appointmentId` | （空） | 预约编号 appointment_no |
| `drugNo` | （空） | 药品编号 drug_no |
| `recordId` | （空） | 病历编号 record_no |
| `prescriptionId` | （空） | 处方编号 prescription_no |
| `notificationId` | （空） | 通知编号 notification_no |

## 启动后端

测试前需启动后端栈：

```bash
# 1. 中间件（MySQL/Redis/Qdrant/RabbitMQ）
docker compose -f infra/docker-compose.yml up -d

# 2. Nacos（独立）
"D:\springcloudalibaba\nacos-server-2.3.0\nacos\bin\startup.cmd" -m standalone

# 3. auth-service + gateway
java -jar medconsult-auth-service/target/medconsult-auth-service-*.jar
java -jar medconsult-gateway/target/medconsult-gateway-*.jar
```

## 错误码参考

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 400001 | 400 | 参数错误 |
| 401001 | 401 | 未认证 / token 无效 |
| 403001 | 403 | 无权限 / 账号禁用 |
| 404001 | 404 | 资源不存在 |
| 409001 | 409 | 冲突（如账号已存在） |
| 500001 | 500 | 服务器内部错误 |
| 502001 | 502 | 外部 AI 调用失败 |
