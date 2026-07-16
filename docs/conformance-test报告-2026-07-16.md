# 三方一致性测试报告（conformance-test）2026-07-16

> 分支：`fix/feat-merge-and-backend`。测试方法：静态契约核对（两轮并行 agent）+ 动态 Playwright 端到端验证（7 个微服务 + gateway + 前端真实后端模式全栈运行）。

## 一、总览

| 维度 | 结果 |
| --- | --- |
| 核对 API 端点（8 模块） | 52 个 |
| ✅ 契约对齐 | 48 |
| ❌ 不一致（已修复） | 1（排班日期过滤参数名） |
| ⚠️ 风险（非阻断） | 3（患者状态 reason / 医生详情无端点 / 预约冗余 doctorId） |
| 后端缺失（已补全） | 3（outpatient @EnableFeignClients / loadbalancer 依赖 / pharmacist_id 列） |
| 动态验证（Playwright） | 登录(管理员+患者)、科室增删(#15)、排班编辑(#17)、改密码(#19)、全局库存流水、患者预约列表 全部通过 |

## 二、动态验证结果（Playwright + 真实后端全栈）

启动全栈：gateway(8080) + auth(8081) + patient(8082) + outpatient(8083) + medical-record(8084) + drug(8085) + notification(8087)，全部注册 Nacos。前端 `VITE_USE_MOCK=false`。

| 业务流 | 网络证据 | 结论 |
| --- | --- | --- |
| 管理员登录 | `POST /auth/login`→200, `GET /auth/me`→200, `GET /auth/users`→200 | ✅ |
| 患者登录 | `POST /auth/login`→200, 重定向 /patient/home | ✅ |
| 科室列表 | `GET /departments`→200（5 科室） | ✅ |
| **科室新增(#15)** | `POST /departments`→200，departmentNo 自动生成(DEP_FSA9IACH3BWH) | ✅ |
| **科室删除(#15)** | `DELETE /departments/DEP_FSA9IACH3BWH`→200 | ✅ |
| 排班列表 | `GET /schedules`→200（4 排班，含 doctorId/departmentId 回填） | ✅ |
| **排班编辑(#17)** | `PUT /schedules/S40001`→200，编辑弹窗医生字段 disabled + 字段回填 | ✅ |
| **改密码(#19)** | `POST /auth/change-password` 旧密码123456→新Test1234 返回200/success；弱密码 123456 返回 400 校验失败 | ✅ |
| **全局库存流水(drug)** | `GET /drugs/stock/flows`→200，total 6，含 drugNo/drugName/batchNo | ✅ |
| 患者预约列表 | `GET /appointments`→200（2 预约，patientName 经 Feign 回填，状态/REFUNDING 映射正确） | ✅ |

## 三、修复清单（conformance-test 发现并修复）

| # | 问题 | 级别 | 修复 | commit |
| --- | --- | --- | --- | --- |
| 1 | 排班日期过滤失效：前端 `startDate/endDate` vs 后端 `dateFrom/dateTo` | ❌契约 | 改前端 ScheduleManage.vue | 上轮 |
| 2 | outpatient 无法启动：缺 `@EnableFeignClients` | 后端缺失 | 补注解 OutpatientServiceApplication.java | 上轮 |
| 3 | outpatient 无法启动：缺 `spring-cloud-starter-loadbalancer` | 后端缺失 | 补 pom.xml 依赖 | ae0bffa |
| 4 | auth 无法启动：`schema.sql` 用 DELIMITER 存储过程，Spring ScriptUtils 不支持 | 环境配置 | 启动加 `--spring.sql.init.mode=never`（DB 已迁移） | start-all.sh |
| 5 | auth 登录 500：sys_user 缺 `pharmacist_id` 列（DELIMITER 过程未执行） | 数据缺失 | ALTER TABLE 补列 | （DB 操作） |

## 四、遗留问题（非阻断，建议后续处理）

| # | 问题 | 级别 | 建议 |
| --- | --- | --- | --- |
| 1 | `PATCH /patients/{id}/status` 前端漏传 `reason`（审计信息弱化） | ⚠️低 | UI 补原因输入框 |
| 2 | `GET /doctors/{id}` 后端无独立详情端点，前端拉全量列表过滤 | ⚠️低 | 医生量大时补详情端点 |
| 3 | 排班时间列显示 `08:00:00`（带秒） | 显示瑕疵 | 前端格式化去秒 |
| 4 | auth `schema.sql` 的 DELIMITER 块需重构为 Spring 可执行的纯 DDL | 配置债 | 改写为幂等 ALTER 或分离迁移脚本 |
| 5 | drug-service 其他写接口（create/inbound/outbound）仍无类级鉴权（既有问题） | 安全 | 后续补 @Permission |
| 6 | change-password 缺频率限制/审计日志 | 安全 | 复用 RateLimiter |

## 五、说明

- AI 服务（ai-service，8086）未启动（需 MongoDB/Milvus/MinIO/阿里云百炼外部依赖），AI 相关接口仅做静态契约核对（全部对齐），未做动态验证。
- zai 视觉 MCP 对本地截图分析超时（工具需远程 URL），改为以 Playwright accessibility snapshot + 网络请求状态码作为动态验证主要手段，结论可靠。
- 测试期间 patient 账号密码曾被改为 Test1234 用于验证，已通过 DB 重置回 123456（bcrypt hash）。
