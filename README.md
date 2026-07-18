# MedConsult-Assist · 智慧医疗问诊辅助系统

面向医疗机构门诊、患者服务与临床辅助场景的 Spring Cloud Alibaba + Vue 3 全栈系统。当前已落地 8 个可运行微服务、7 个 common library、四角色前端和 AI/RAG 链路，正在按 [`项目实施基线`](./docs/项目实施基线.md) 收敛权限、测试覆盖、数据治理、运行依赖和全角色体验。

## 技术栈（冻结）

详见 [`AGENTS.md` 红线 1](./AGENTS.md) 与 [`docs/架构设计文档.md` §0](./docs/架构设计文档.md)。**不得擅自升级或替换。**

| 组件 | 锁定版本 |
| --- | --- |
| JDK | Temurin **21.0.11** (LTS) |
| Spring Boot | **3.2.0** |
| Spring Cloud | **2023.0.0** |
| Spring Cloud Alibaba | **2022.0.0.0**（仅 Nacos 注册/配置 + Gateway + OpenFeign） |
| 持久层 | MyBatis-Plus **3.5.5** + MySQL 8.0 |
| 缓存与消息 | Redis + RabbitMQ |
| AI 接入 | LangChain4j + OpenAI 兼容协议 |
| 向量库 | Milvus（主）/ Qdrant（可选，当前未承载运行检索） |
| 前端与部署 | Vue 3 + Docker + GitLab CI/CD |

> ⚠️ **已知风险（红线 1 明确，不必动）**：SCA 2022.0.0.0 官方针对 Boot 3.0.x，与 Boot 3.2.0 无兼容矩阵。缓解方式是 pom BOM 顺序 + 手动排除冲突传递依赖 + Nacos 启动冒烟，**而非升级版本**。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [`docs/项目实施基线.md`](./docs/项目实施基线.md) | **后续修改入口**：医疗场景、当前实现、质量门禁与数据边界 |
| [`docs/需求文档.md`](./docs/需求文档.md) | 业务范围与验收标准（源头） |
| [`docs/架构设计文档.md`](./docs/架构设计文档.md) | 服务边界、调用、可靠性和 AI/RAG 架构 |
| [`docs/数据库设计文档.md`](./docs/数据库设计文档.md) | 逻辑数据模型；当前 DDL 以各服务 `schema*.sql` 交叉验证 |
| [`docs/接口文档.md`](./docs/接口文档.md) | `/api/v1/*` 对外与流式接口契约 |
| [`docs/全栈契约矩阵.md`](./docs/全栈契约矩阵.md) | 文档、前端调用、后端实现和测试的对应关系 |
| [`docs/修改建议.md`](./docs/修改建议.md) | review 第 0 步问题总览和当前实施状态 |
| [`docs/遗留问题复盘与实施状态.md`](./docs/遗留问题复盘与实施状态.md) | 状态证据、历史快照与后续动作 |

## 构建要求

- JDK **21**（推荐 Temurin 21.0.11）
- Maven 3.9+（仓库已附带 Maven wrapper，无需预装）

## 构建

```bash
# 使用系统 mvn
mvn clean install

# 或使用 wrapper（首次会下载 Maven）—— 在 backend/ 目录下执行
cd backend
./mvnw clean install        # Linux/macOS/Git Bash
mvnw.cmd clean install      # Windows cmd/powershell
```

## 工程结构

```text
frontend/                       Vue 3 四角色前端与 Playwright E2E
backend/
├── pom.xml                     微服务父工程
├── gateway                     API 网关
├── auth-service                认证、用户、服务账号与 RBAC
├── patient-service             患者档案
├── outpatient-service          科室、医生、排班、预约与退款
├── medical-record-service      病历、附件与处方
├── drug-service                药品、批次、FEFO 与库存流水
├── notification-service        通知、审计与 MQ 消费
├── ai-service                  分诊、问诊、摘要、用药、影像与 AI 治理
├── medconsult-common/          7 个 library 子模块，无生产启动类
└── data/                       Mongo/Milvus 医疗知识导入工具
infra/                          MySQL、Redis、RabbitMQ、Milvus、Mongo、MinIO、Embedding
scripts/                        启停、契约检查与可审查数据工具
docs/                           需求、架构、接口、数据和治理基线
```

## 开发约定

- **工作语言**：中文（文档与 commit message）。
- **分支策略**：个人分支 `docs/suggestions-<github用户名>`，**禁止直推 main**。
- **提交**：完成一个内聚改动立即本地 commit。
- **远端操作**：未经用户明确授权，agent 不得 push，也不得创建 PR / MR；合并前仍必须 review。

## 落地路线图

当前实施顺序见 [`项目实施基线 §8`](./docs/项目实施基线.md) 和 [`长期治理实现计划`](./docs/superpowers/plans/2026-07-17-project-analysis-and-long-term-review-roadmap.md)。每轮 review 必须先核对 [`修改建议.md` 第一节](./docs/修改建议.md)，再按测试、运行依赖、权限/owner、数据对账和前端真实流程逐项关闭。
