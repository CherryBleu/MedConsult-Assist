# MedConsult-Assist · 智慧医疗问诊辅助系统

面向医疗机构门诊与临床辅助场景的 Spring Cloud Alibaba 微服务系统。本仓库当前处于**设计 → 实现过渡阶段**：架构设计已锁定，公共模块地基 `medconsult-common` 正在落地。

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
| AI 接入 | Spring AI / LangChain4j |
| 向量库 | Milvus / Qdrant |
| 前端与部署 | Vue 3 + Docker + GitLab CI/CD |

> ⚠️ **已知风险（红线 1 明确，不必动）**：SCA 2022.0.0.0 官方针对 Boot 3.0.x，与 Boot 3.2.0 无兼容矩阵。缓解方式是 pom BOM 顺序 + 手动排除冲突传递依赖 + Nacos 启动冒烟，**而非升级版本**。

## 文档导航

| 文档 | 用途 |
| --- | --- |
| [`docs/需求文档.md`](./docs/需求文档.md) | 业务范围与验收标准（源头） |
| [`docs/数据库设计文档.md`](./docs/数据库设计文档.md) | MySQL 表结构原始设计 |
| [`docs/接口文档.md`](./docs/接口文档.md) | `/api/v1/*` 对外接口原始设计 |
| [`docs/修改建议.md`](./docs/修改建议.md) | **架构决策权威书**——矛盾以此为准 |
| [`docs/架构设计文档.md`](./docs/架构设计文档.md) | 面向开发可落地的 12 章架构设计（含多实例水平扩展） |

## 构建要求

- JDK **21**（推荐 Temurin 21.0.11）
- Maven 3.9+（仓库已附带 Maven wrapper，无需预装）

## 构建

```bash
# 使用系统 mvn
mvn clean install

# 或使用 wrapper（首次会下载 Maven）
./mvnw clean install        # Linux/macOS/Git Bash
mvnw.cmd clean install      # Windows cmd/powershell
```

## 工程结构

```
medconsult-parent            顶层父 pom：技术栈锁定 + BOM 仲裁
└── medconsult-common        公共模块聚合（7 子模块，详见架构文档 §3）
    ├── medconsult-common-core      Result/ErrorCode/异常/分页
    ├── medconsult-common-web       全局异常/traceId/脱敏（待实现）
    ├── medconsult-common-security  JWT/@Permission/数据范围（待实现）
    ├── medconsult-common-mybatis   MyBatis-Plus 配置/自动填充（待实现）
    ├── medconsult-common-mq        本地消息表/RabbitMQ（待实现）
    ├── medconsult-common-redis     分布式锁/限流/SSE广播（待实现）
    └── medconsult-common-feign     鉴权拦截器/错误解码（待实现）
```

业务服务模块（gateway / auth-service / patient-service / outpatient-service / medical-record-service / drug-service / ai-service / notification-service）按 [`架构设计文档.md §11`](./docs/架构设计文档.md) 的落地路线图分阶段加入。

## 开发约定

- **工作语言**：中文（文档与 commit message）。
- **分支策略**：个人分支 `docs/suggestions-<github用户名>`，**禁止直推 main**。
- **PR**：合并前必须经过 review；PAT 无开 PR 权限，需在 GitHub 网页端手动开启。

## 落地路线图

参见 [`docs/架构设计文档.md §11`](./docs/架构设计文档.md)：
- **第 0 阶段**（进行中）：`medconsult-common` 公共模块地基。
- **第一阶段**：auth / patient / medical-record / drug / notification 业务闭环（P0）。
- **第二阶段**：ai-service 全部 AI 能力 + 内部 Feign 契约（P0）。
- **第三阶段**：SSE / RabbitMQ / Redis 锁限流 / Function Calling（P1）。
- **第四阶段**：字段一致性 / 加密 / 就诊闭环 / 多实例部署验证（P1/P2）。
