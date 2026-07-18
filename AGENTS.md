# AGENTS.md — Agent 协作约定（MedConsult-Assist）

本文件为 AI agent（ZCode / Claude Code 等）在本仓库工作时的行为约定。所有 agent 必须遵守。

## Git 工作流约定

### 核心原则：勤 commit，少 push

- **勤 commit**：完成一个内聚的改动单元（一个修复、一个功能、一组相关的 review 修复）就立即 `git commit`，把工作切小、留下清晰可回溯的提交历史。不要攒一大堆改动一次性提交。
- **少 push**：未经用户明确指示，**不要 `git push`**。本地 commit 即可，push 是面向远端的不可逆操作，必须由用户决定时机。
- **不擅自创建 PR**：未经用户明确指示，**不要创建 Pull Request / Merge Request**。创建 PR 通常需要先 push，二者都必须由用户决定时机。
- 一个 commit 只做一件事：混合多个无关改动的提交会污染历史、增加回滚难度。拆成多个小 commit。
- commit message 遵循仓库现有风格（Conventional Commits 中文描述，如 `fix(ai-service): ...`、`feat(integration): ...`）。

### 何时 commit / 何时 push

| 场景 | commit | push |
| --- | --- | --- |
| 修完一个 bug / 一轮 review 修复 | ✅ 立即 | ❌ |
| 完成一个功能模块 | ✅ 立即 | ❌ |
| 改动验证通过（编译/测试） | ✅ 立即 | ❌ |
| 用户说"提交" / "commit" | ✅ | ❌ |
| 用户说"推送" / "push" / "推上去" | — | ✅ |
| 创建 PR 前 | ✅（已提交） | ✅（需 push 才能开 PR） |

### 分支

- 不在 `main` 上直接开发：开始非平凡改动前先建分支（如 `fix/ai-authz`、`review/round-1`）。
- 只读探索 / 不改代码时无需建分支。

## 代码风格与质量

- 匹配周围代码的命名、注释密度、惯用法——写"读起来像这个仓库本来就有的"代码。
- 改动尽量小而内聚，避免无关的重构夹带。
- 修复后必须验证（编译 / 测试 / 构建）；验证失败要如实说明，不得谎称"已修复"。
- 声称覆盖率达标时必须附当前分支生成的 JaCoCo（后端）或对应覆盖率工具报告；测试数量和“测试通过”不能替代覆盖率证据。

## 运行配置约定

- `.vscode/launch.json` 只配置可部署的 Spring Boot 微服务，不配置 `medconsult-common`、`data` 等 library / tool 模块。
- common 模块允许在 `src/test` 中保留 `@SpringBootApplication` 测试配置锚点，但不得提供 `main()`，避免被 Spring Boot Dashboard 误识别为可运行服务。
- VS Code 启动配置不得引用仓库中不存在的必需 `envFile`；敏感环境文件仍不得提交。

## Review 流程约定

`docs/修改建议.md` 是本仓库的**问题总览清单**，fullstack-review / code-review / conformance-test 等 review 类工作的**第 0 步必须逐条核对它的总览表**（第一节"问题总览"），为每条标注当前状态（已实现 / 部分实现 / 未实现 / 不适用），再进入常规 review。

- 这一约定源自第 6 轮复盘：前 5 轮 review 未把修改建议.md 当 checklist 用，导致"DDL 已建、实现欠债"的架构遗留问题（RBAC 五表、症状规则表、id_no 加密、退费、审计 MQ 生产端）长期未被检出。
- 核对结果应写入 review 总结，并在 `docs/修改建议.md` 总览表或 `docs/遗留问题复盘与实施状态.md` 中同步状态，形成闭环。
- 当某条建议的优先级或方案发生变化（如本仓库已将"症状自诊不调用 LLM"放宽为"允许叠加 LLM"），必须在文档中显式记录修订，不得只改代码不更新基线。

## 安全与破坏性操作

- 删除 / 覆盖文件前先看目标内容；若与描述不符或非自己创建，先停下来问用户。
- 不向外部服务发送内容（发布、索引）除非用户明确授权。
- `git push --force`、`git reset --hard` 远端分支、删除数据卷等操作需用户明确同意。
- 批量 `DELETE` / `TRUNCATE`、Redis 批量删键、对象存储批量删除必须先提供 dry-run 清单、白名单、备份与验证步骤；执行前停止业务写入，并再次确认目标环境和保留数据范围。
- 医疗知识库与用户业务数据必须分域处理：Mongo/Milvus 知识数据、Milvus 内部 MinIO/etcd、应用影像 MinIO 不得混删。
