# AI 智能域实现说明

本文档说明 `ai` 模块的实现范围、接口覆盖、数据库表、症状自诊链路和验收方式。详细启动与联调步骤见 [ai/README.md](../ai/README.md)。

## 实现范围

已实现接口：

| 功能 | 接口 |
| --- | --- |
| 症状自诊问答 | `POST /api/v1/ai/symptom-chat` |
| 智能分诊推荐 | `POST /api/v1/ai/triage` |
| 病历摘要 | `POST /api/v1/ai/medical-record-summary` |
| 文本病历摘要 | `POST /api/v1/ai/medical-record-summary/text` |
| 医生确认摘要 | `POST /api/v1/medical-records/{recordId}/summary/confirm` |
| 用药分析 | `POST /api/v1/ai/medication-analysis` |
| 影像异常检测 | `POST /api/v1/ai/imaging-abnormal-detection` |
| 查询影像检测结果 | `GET /api/v1/ai/imaging-abnormal-detection/{detectionId}` |
| 医生复核影像结果 | `POST /api/v1/ai/imaging-abnormal-detection/{detectionId}/review` |
| 提交 AI 反馈 | `POST /api/v1/ai/feedback` |
| 查询 AI 反馈 | `GET /api/v1/ai/feedback` |
| 查询 AI 调用日志 | `GET /api/v1/ai/call-logs` |

统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "trace-xxx",
  "timestamp": "2026-07-08T15:30:00+08:00"
}
```

## 技术栈遵循

当前实现保持需求文档约束：

- JDK 21
- Spring Boot 3.x
- MyBatis-Plus
- MySQL 8.0
- Redis
- RabbitMQ
- MongoDB
- Milvus
- OpenAI-compatible 大模型与 Embedding HTTP API

没有替换或新增非文档要求的核心技术栈。

## 数据库表

AI 域建表脚本位于：

```text
ai/src/main/resources/db/schema-ai.sql
```

覆盖表：

- `ai_chat_session`
- `ai_chat_message`
- `ai_triage_result`
- `ai_medical_summary`
- `ai_medication_analysis`
- `ai_imaging_detection`
- `ai_feedback`
- `ai_call_log`

向量索引结构不落 MySQL，仍由 Milvus 保存；疾病原始知识来源仍为疾病 JSON 派生数据。

## 症状自诊问答链路

本次实现严格参考 `demo` 的主要流程，并按用户要求在最终回答阶段再次调用大模型。

链路如下：

```text
用户输入
  -> 大模型标准化：top3 疾病候选、症状、描述、元数据查询字段
  -> 编码异常保护与口语化症状扩展
  -> Redis 缓存
  -> MongoDB name 精确匹配
  -> Milvus 语义检索兜底
  -> 疾病 JSON 命中结果去重
  -> 高危症状规则
  -> 将命中知识、引用、患者上下文、风险规则交给大模型生成最终回答
  -> 写会话、消息、调用日志
```

关键类：

| 类 | 责任 |
| --- | --- |
| `DiseaseSearchService` | 标准化、缓存、MongoDB、Milvus 检索编排 |
| `DiseaseCacheService` | Redis 标准化结果缓存 |
| `MongoDiseaseRepository` | MongoDB `name` 精确匹配 |
| `MilvusRestClient` | Milvus REST 向量检索 |
| `QueryExpander` | 口语化症状扩展 |
| `EncodingComplaintGuard` | 可读中文被误判乱码时的纠偏 |
| `RiskRuleEngine` | 高危症状规则 |
| `SymptomChatService` | 自诊问答总编排与最终 LLM 回答 |

## 风险控制

症状自诊最终回答必须满足：

- 不作确定诊断。
- 不给处方。
- 不编造命中疾病 JSON 之外的医学依据。
- 必须提示“非诊断，仅供参考，不能替代医生诊断”。
- 命中胸痛、呼吸困难、意识障碍、大出血等高危症状时，返回急诊建议。

## 验证

已执行：

```powershell
cd ai
mvn -q test
```

结果：通过。

测试覆盖：

- 口语化“鸡叫样咳嗽”扩展后应更倾向百日咳相关匹配。
- 高危症状规则命中后应返回急诊建议。

## 后续联调建议

1. 执行 `schema-ai.sql` 初始化 AI 域表。
2. 导入疾病 JSON 到 MongoDB，并确认 `name` 字段索引存在。
3. 使用 `data` 模块将疾病 JSON 派生文本导入 Milvus。
4. 配置 LLM 与 Embedding API Key。
5. 启动 `ai` 服务并使用接口文档样例联调。
