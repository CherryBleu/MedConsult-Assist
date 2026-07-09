# MedConsult AI Postman API 测试集合

本目录提供 AI 智能域接口的 Postman 测试集合，方便手动测试、冒烟验证与前后端联调。

## 文件清单

| 文件 | 说明 |
|---|---|
| `MedConsult-AI.postman_collection.json` | AI 接口集合，含接口定义、请求示例、基础断言和变量自动保存脚本 |
| `MedConsult-AI.postman_environment.json` | 本地环境变量，含 `baseUrl`、`patientId`、`recordId`、`summaryId`、`detectionId` 等 |

## 导入步骤

1. 打开 Postman -> Import -> 拖入两个 JSON 文件。
2. 右上角环境选择器选「MedConsult AI 本地」。
3. 确认 `baseUrl` 为 `http://localhost:8082`。
4. 先启动 AI 服务，再按下方流程执行接口。

## 已覆盖接口

### AI 症状自诊问答

| # | 方法 | 路径 | 说明 | 前置 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/ai/symptom-chat` | 症状自诊问答，返回回答、风险等级、可能原因、推荐科室、引用和向量命中 | 建议配置 MongoDB/Milvus/LLM；未配置会降级 |

### AI 智能分诊

| # | 方法 | 路径 | 说明 | 前置 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/ai/triage` | 根据症状推荐科室，返回置信度、优先级和急诊标识 | 建议配置疾病知识库 |

### AI 病历摘要

| # | 方法 | 路径 | 说明 | 前置 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/ai/medical-record-summary/text` | 根据病历文本生成摘要，适合优先冒烟 | 无 |
| 2 | POST | `/api/v1/ai/medical-record-summary` | 根据病历编号生成摘要，成功后自动保存 `summaryId` | MySQL 存在 `record_no={{recordId}}` 的 `medical_record` |
| 3 | POST | `/api/v1/medical-records/{recordId}/summary/confirm` | 医生确认摘要 | 已自动保存 `summaryId` |

### AI 用药分析

| # | 方法 | 路径 | 说明 | 前置 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/ai/medication-analysis` | 用药风险、相互作用、禁忌和提醒；成功后自动保存 `analysisId`、`aiResultId` | 无；未配置 LLM 会走本地规则 |

### AI 影像异常检测

| # | 方法 | 路径 | 说明 | 前置 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/ai/imaging-abnormal-detection` | 提交影像报告异常检测；成功后自动保存 `detectionId`、`aiResultId` | 无；未配置模型会走关键词兜底 |
| 2 | GET | `/api/v1/ai/imaging-abnormal-detection/{detectionId}` | 查询影像检测结果 | 已自动保存 `detectionId` |
| 3 | POST | `/api/v1/ai/imaging-abnormal-detection/{detectionId}/review` | 医生复核影像 AI 结果 | 已自动保存 `detectionId` |

### AI 反馈与调用日志

| # | 方法 | 路径 | 说明 | 前置 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/ai/feedback` | 提交 AI 结果反馈，成功后自动保存 `feedbackId` | 已自动保存 `aiResultType` 和 `aiResultId` |
| 2 | GET | `/api/v1/ai/feedback` | 查询 AI 反馈 | 已自动保存 `aiResultType` 和 `aiResultId` |
| 3 | GET | `/api/v1/ai/call-logs` | 查询 AI 调用日志 | 执行过任意会写日志的接口 |

## 自动脚本说明

集合内置 Post-response/Test 脚本：

- 所有接口自动断言 HTTP 2xx。
- 所有 JSON 响应自动断言统一响应字段：`code`、`message`、`traceId`、`timestamp`。
- 病历编号摘要成功后自动保存 `summaryId`。
- 用药分析成功后自动保存 `analysisId`，并把 `aiResultType=MEDICATION_ANALYSIS`、`aiResultId={{analysisId}}` 写入环境变量。
- 影像检测成功后自动保存 `detectionId`，并把 `aiResultType=IMAGING_DETECTION`、`aiResultId={{detectionId}}` 写入环境变量。
- 提交反馈成功后自动保存 `feedbackId`。

集合级 Auth 使用 `Bearer {{accessToken}}`。当前 AI 模块未实现认证拦截时，`accessToken` 可为空；如果通过 Gateway 联调，把环境变量 `baseUrl` 改成 Gateway 地址，并填入登录获得的 token。

## 推荐测试流程

```text
# 最小冒烟流程，不依赖病历表数据
1. POST /ai/medical-record-summary/text
2. POST /ai/medication-analysis -> 自动保存 analysisId / aiResultId
3. POST /ai/feedback
4. GET  /ai/feedback
5. GET  /ai/call-logs

# 影像流程
6. POST /ai/imaging-abnormal-detection -> 自动保存 detectionId / aiResultId
7. GET  /ai/imaging-abnormal-detection/{detectionId}
8. POST /ai/imaging-abnormal-detection/{detectionId}/review

# RAG 流程
9. POST /ai/symptom-chat
10. POST /ai/triage

# 病历编号摘要流程，依赖 medical_record 测试数据
11. 准备 record_no={{recordId}} 的 medical_record 数据
12. POST /ai/medical-record-summary -> 自动保存 summaryId
13. POST /medical-records/{recordId}/summary/confirm
```

症状自诊的 Postman 样例使用“肺栓塞 + 胸痛/胸闷/呼吸困难/咯血 + 科室/检查问题”的组合。该疾病在统一疾病 JSON 中有完整条目，正常导入 MongoDB/Milvus 后，响应中应能看到 `possibleCauses`、`suggestedDepartments`、`vectorMatches`、`citations` 等字段有实际内容。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `baseUrl` | `http://localhost:8082` | AI 服务地址；若走 Gateway 可改成 `http://localhost:8080` |
| `accessToken` | 空 | 可选。通过 Gateway 鉴权时填写 |
| `sessionId` | `CHAT202607060001` | 症状自诊会话编号 |
| `patientId` | `P202607060001` | 患者业务编号 |
| `recordId` | `MR202607060001` | 病历业务编号 |
| `summaryId` | 空 | 病历编号摘要成功后自动填充 |
| `analysisId` | 空 | 用药分析成功后自动填充 |
| `detectionId` | 空 | 影像检测成功后自动填充 |
| `feedbackId` | 空 | 提交反馈成功后自动填充 |
| `aiResultType` | `MEDICATION_ANALYSIS` | 反馈查询用 AI 结果类型 |
| `aiResultId` | 空 | 用药分析或影像检测成功后自动填充 |

## 启动后端

测试前需启动 AI 模块依赖的基础设施和服务。

```powershell
# 1. 启动 MySQL / Redis / RabbitMQ / MongoDB / Milvus
# 按你的本地 infra 或 Docker Compose 启动即可

# 2. 初始化 AI 域表
# 在 MySQL 中执行：
# ai/src/main/resources/db/schema-ai.sql

# 3. 启动 AI 服务
cd ai
mvn spring-boot:run
```

服务默认地址：

```text
http://localhost:8082/api/v1
```

## 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `/ai/medical-record-summary` 返回 `404001` | `medical_record` 表没有 `record_no={{recordId}}` 的数据 | 先插入测试病历，或先测 `/ai/medical-record-summary/text` |
| `/medical-records/{recordId}/summary/confirm` 失败 | `summaryId` 为空或不存在 | 先成功执行 `/ai/medical-record-summary` |
| `/ai/symptom-chat` 返回依据不足 | MongoDB/Milvus 疾病知识未准备好，或 LLM 未能抽取到可命中的疾病名 | 导入疾病 JSON 到 MongoDB，并用 data 模块导入 Milvus；可先使用集合内“肺栓塞”样例验证完整返回 |
| LLM 相关接口返回模板化结果 | 未配置 `OPENAI_API_KEY` 或模型调用失败 | 配置 `OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL` |
| Postman 变量没更新 | 上一个接口没有成功返回 `code=0` | 查看响应体错误码和控制台日志 |

## 错误码参考

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 400001 | 400 | 参数错误 |
| 401001 | 401 | 未认证 / token 无效 |
| 403001 | 403 | 无权限 / 账号禁用 |
| 404001 | 404 | 资源不存在 |
| 409001 | 409 | 业务冲突 |
| 500001 | 500 | 服务器内部错误 |
| 502001 | 502 | 外部 AI 调用失败 |
