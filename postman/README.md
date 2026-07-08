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

> **内部接口**（`/internal/patients/{id}/context`、`/internal/patients/{id}/allergies`）不经 Gateway，Postman 不直接覆盖——它们供 ai-service 通过 Feign 调用。如需手动测试内部接口，可直连 patient-service:8082。

### 待加入（随服务实现同步更新）

- outpatient-service（§2.3-2.5）：科室/医生/排班/预约
- medical-record-service（§2.6）：病历/处方
- drug-service（§2.7）：药品/库存
- ai-service（§3）：症状自诊/分诊/摘要/用药/影像
- notification-service（§4）：通知/审计

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
