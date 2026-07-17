# MedConsult-Assist 8–12 周长期治理实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 用 8–12 周把 `docs/`、前端、后端、测试与工程化治理为可验证、可回归、可持续 review 的完整系统，优先关闭事实基线、核心测试、安全、性能和移动端体验风险。

**架构：** 采用“风险与证据优先”的单线推进方式。先校准文档与测试地基，再闭合安全和业务链路，然后优化后端 I/O 与复杂度，最后治理前端 UI/UX、CI、可观测性和契约回归。每个工作包执行“失败测试 → 最小实现 → 实测 → 2–4 轮 review → 文档回写 → 独立 commit”，未经用户明确指示不 push。

**技术栈：** Java 21、Spring Boot 3.2.0、Spring Cloud 2023.0.0、Spring Cloud Alibaba 2022.0.0.0、MyBatis-Plus 3.5.5、MySQL 8、Redis、RabbitMQ、Vue 3、Element Plus、Vite 2、Playwright、Testcontainers、GitHub Actions、Spring Boot Actuator、Micrometer、Prometheus。

**关联规格：** `docs/superpowers/specs/2026-07-17-project-analysis-and-long-term-review-design.md`

---

## 一、执行原则与周次

### 1.1 资源与分支

- 按 AI 主导单线执行，同一时间只允许一个生产代码工作包处于 `in_progress`。
- 只读扫描可并行；会修改共享文件的任务必须串行。
- 当前工作区存在用户未提交的 `MedicalRecordFlowTest.java`。实施必须从已提交 `HEAD` 创建独立 worktree，禁止 stash、覆盖或提交该 WIP。
- 每个任务独立 commit，不 push。
- 每次暂存前运行 `git status --short`；`git add` 只列任务“文件”小节中的具体文件，不使用模块根目录或 `src/main`、`src/views` 等宽泛目录；提交前运行 `git diff --cached --name-only`，出现任务外文件立即 `git reset` 并重新精确暂存。
- 命令块默认按 Git Bash 写法执行；在 PowerShell 执行时必须改为等价命令并给 `-D...` Maven 参数加引号。任何 heredoc、反斜杠续行或 Bash 花括号展开都不得原样贴到 PowerShell。
- 任务 3/4/20 依赖 Docker/Testcontainers。实施前必须运行 `docker version` 和一个最小 Testcontainers smoke test；Docker 不可用时标记 `BLOCKED-ENVIRONMENT`，不得把真实 MySQL/Redis 门禁降级为 H2 绿色。

### 1.2 推荐周次

| 周次 | 阶段 | 核心产物 | 退出门禁 |
| --- | --- | --- | --- |
| 第 1–2 周 | 事实与测试地基 | 15 项状态表、契约矩阵、可运行 FlowTest、分页基线 | docs 状态可复核；门诊 6 个 FlowTest 执行断言 |
| 第 3–4 周 | 安全与审计 | RBAC 驱动 scope、入口角色闭环、患者签到权限、行级过滤、审计 outbox | 越权测试、scope 测试、患者签到和医生推进状态测试、审计投递与幂等测试通过 |
| 第 5–6 周 | 业务与性能 | 症状规则、退费、处方前端闭环、用户列表 O(pageSize)、药品风险批量、调剂异步状态机 | 复杂度和远程调用数下降；故障注入无数据漂移；医生开方→患者缴费→药师发药 E2E 通过 |
| 第 7–8 周 | 前端体验 | 响应式骨架、无障碍、错误恢复、四角色设计系统 | 375/768/1024/1440 视口通过；键盘路径可用 |
| 第 9–10 周 | 工程治理 | 指标、健康检查、CI、契约检查、前端包体积治理 | PR 检查自动执行；关键指标可查询 |
| 第 11–12 周 | 收敛 | 全量回归、局部压测、docs 回写、遗留台账 | 连续一轮三线 review 无新高/中风险问题 |

若某阶段提前满足门禁，可以提前进入下一阶段；不得因周次到期跳过失败门禁。

---

## 二、文件结构总览

### 2.1 第 1–2 周：基线与测试

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `docs/全栈契约矩阵.md` | 记录 docs ↔ 前端 ↔ 后端 ↔ 数据 ↔ 权限 ↔ 测试 |
| 创建 | `scripts/__init__.py`、`scripts/review/__init__.py` | 将契约检查器声明为可导入的 Python 包 |
| 创建 | `scripts/review/verify_contract_matrix.py` | 检查契约矩阵中的路径能在 docs、前端和后端命中 |
| 创建 | `scripts/review/test_verify_contract_matrix.py` | 验证契约检查器的正常、缺漏和误报分支 |
| 修改 | `docs/修改建议.md` | 校准 15 项状态和证据 |
| 修改 | `docs/数据库设计文档.md` | 移除已落地表仍被列为缺口的错误表述 |
| 修改 | `docs/接口文档.md` | 统一分页默认值，补 AI、文件上传与 SSE 契约 |
| 修改 | `docs/架构设计文档.md` | 校准 internal Feign、影像拆表、上传链路和消息现状 |
| 修改 | `docs/遗留问题复盘与实施状态.md` | 增加 2026-07-17 当前状态，不覆盖历史复盘 |
| 创建 | `backend/outpatient-service/src/test/java/com/medconsult/outpatient/support/OutpatientTestContainers.java` | 提供真实 MySQL 8 与 Redis 测试容器 |
| 修改 | `backend/outpatient-service/src/test/java/com/medconsult/outpatient/OutpatientFlowTest.java` | 移除 H2 方言依赖，接入容器 |
| 修改 | `backend/outpatient-service/pom.xml` | 增加 Testcontainers 测试依赖 |
| 修改 | `backend/medconsult-common/common-mybatis/src/test/java/com/medconsult/common/mybatis/MybatisPlusFlowTest.java` | 增加分页插件行为测试 |
| 修改 | `backend/auth-service/pom.xml` | 增加 MySQL/Redis Testcontainers 测试依赖 |
| 创建 | `backend/auth-service/src/test/java/com/medconsult/auth/support/AuthTestContainers.java` | 提供真实 MySQL 8 与 Redis 测试容器 |
| 创建 | `backend/auth-service/src/test/java/com/medconsult/auth/AuthPaginationFlowTest.java` | 在真实 MySQL 上验证分页自动配置与 SQL LIMIT |

### 2.2 第 3–6 周：安全、业务与性能

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `backend/auth-service/src/main/java/com/medconsult/auth/user/service/RbacQueryService.java` | 批量查询用户角色与权限 scope，提供缓存和灰度兜底 |
| 修改 | `backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java` | 登录、刷新、绑定、列表改用 RBAC 与 DB 分页 |
| 修改 | `backend/auth-service/src/main/java/com/medconsult/auth/DataSeeder.java` | 补角色、权限和用户角色种子 |
| 创建 | `backend/auth-service/src/test/java/com/medconsult/auth/user/service/RbacQueryServiceTest.java` | 覆盖 DB 命中、缓存、空权限和兜底开关 |
| 创建 | `backend/medical-record-service/src/test/java/com/medconsult/medicalrecord/MedicalRecordAuthorizationTest.java` | 不触碰用户 WIP，单独验证 owner 与医生范围 |
| 修改 | `backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java` | 把 SELF/ASSIGNED 约束下沉到查询条件 |
| 修改 | `backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/controller/AppointmentController.java`、`frontend/src/api/appointment.js`、`frontend/src/views/patient/appointment/MyAppointment.vue` | 修复患者签到 403，医生才可推进叫号/完成 |
| 修改 | `backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/medicalrecord/service/MedicalRecordServiceImpl.java` | 把 SELF/ASSIGNED 约束下沉到查询条件 |
| 创建 | `backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditLogEvent.java` | 共享审计事件模型 |
| 创建 | `backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditOutboxService.java` | 在业务事务内写 local_message |
| 修改 | auth、patient、outpatient、drug、medical-record 服务的 `schema.sql`、`pom.xml`、`application.yml` | 接入 outbox、confirm 和本地消息表 |
| 修改 | `AuthServiceImpl.java`、`PatientServiceImpl.java`、`AppointmentTxService.java`、`MedicalRecordServiceImpl.java`、`PrescriptionTxService.java`、`DrugStockTxService.java` | 在同一事务内显式入队审计事件 |
| 创建 | `backend/medconsult-common/common-crypto/` | AES-256-GCM 与 SHA-256 盲索引基础设施 |
| 修改 | `backend/patient-service/src/main/java/com/medconsult/patient/entity/Patient.java`、`backend/patient-service/src/main/java/com/medconsult/patient/service/PatientServiceImpl.java`、`backend/patient-service/src/main/resources/schema.sql` | 双写密文与 hash，移除明文模糊搜索 |
| 创建 | `backend/patient-service/src/main/java/com/medconsult/patient/service/IdNoMigrationRunner.java` | 有界批量、可重入迁移存量证件号 |
| 创建 | `backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/`、`persistence/mapper/` 下的 3 组规则类型与 `RiskRuleEngineDatabaseTest.java` | 将症状规则表接入运行链路，保留硬编码降级 |
| 创建 | `backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/`、`frontend/src/api/appointment.js` 与 `MyAppointment.vue` 退款入口 | 完成退款单状态机和幂等，并禁止通用 payment patch 伪造退款 |
| 修改 | `backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/client/DrugFeignClient.java`、`backend/drug-service/src/main/java/com/medconsult/drug/controller/DrugInternalController.java`、`backend/drug-service/src/main/java/com/medconsult/drug/service/DrugService.java` | 增加批量药品风险查询 |
| 修改 | `backend/ai-service/src/main/java/com/medconsult/ai/service/MedicationFunctionService.java`、`frontend/src/views/doctor/ai-tool/MedicationAnalysis.vue`、`frontend/src/api/ai.js` | O(D) 次 Feign 降为 1 次，并禁止前端用 placeholder 药品假成功 |
| 修改 | `backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/`、`backend/drug-service/src/main/java/com/medconsult/drug/`、`backend/medconsult-common/common-mq/` | 调剂改为 DISPENSING 异步状态机 |
| 修改 | `frontend/src/api/prescription.js`、`frontend/src/views/doctor/record/RecordWrite.vue`、`frontend/src/router/modules/patient.js`、患者处方页与 E2E | 补齐医生结构化开方/提交、患者列表/详情/缴费 |
| 修改 | `backend/notification-service/src/main/resources/schema.sql`、`backend/ai-service/src/main/resources/db/schema-ai.sql` | 基于 `EXPLAIN ANALYZE` 增加必要复合索引 |

### 2.3 第 7–12 周：前端与工程治理

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 修改 | `frontend/index.html` | 正确语言、标题与基础元数据 |
| 修改 | `frontend/src/styles/variables.css`、`global.css` | 响应式与可访问性设计令牌 |
| 创建 | `frontend/src/composables/useResponsive.js` | 统一移动端/桌面端断点状态 |
| 创建 | `frontend/src/components/common/PageState.vue` | loading、empty、error、retry 统一状态 |
| 创建 | `frontend/src/components/common/ResponsiveTable.vue` | 桌面表格与移动卡片切换 |
| 修改 | `frontend/src/layouts/MainLayout.vue` | 移动导航、键盘语义和通知可访问性 |
| 修改 | 登录、注册、四角色工作台与关键列表页 | 分批应用响应式和统一组件 |
| 创建 | `frontend/e2e/mobile-responsive.spec.ts` | 多视口溢出和移动导航回归 |
| 创建 | `frontend/e2e/accessibility.spec.ts` | 键盘、可访问名称与 axe 回归 |
| 创建 | `frontend/e2e/error-recovery.spec.ts` | 接口失败与重试回归 |
| 修改 | `frontend/vite.config.mjs`、`src/main.js` | 按测量结果优化 Element Plus 与 vendor 包体积 |
| 修改 | 8 个运行模块 `pom.xml` 与 `application.yml` | Actuator、Prometheus、健康检查 |
| 创建 | `.github/workflows/ci.yml` | 后端、前端、Playwright、契约检查门禁 |
| 创建 | `scripts/perf/k6/*.js` | 用户列表、预约、用药、调剂基线与回归 |
| 修改 | 4 份核心 docs 与总览/复盘 | 最终状态闭环 |

所有生产文件都在具体任务中再次列出完整路径；执行者不得根据模糊目录猜测目标文件。

---

## 三、第 1–2 周：事实校准与可运行测试地基

### 任务 0：隔离工作区并冻结基线

**文件：**
- 不修改生产文件
- 生成到工作区外：测试日志与性能基线产物

- [ ] **步骤 1：确认当前分支和用户 WIP**

运行：

```bash
git status --short
git branch --show-current
git rev-parse HEAD
```

预期：能看到用户的 `backend/medical-record-service/src/test/java/com/medconsult/medicalrecord/MedicalRecordFlowTest.java` 修改；记录 HEAD，禁止 add 该文件。

- [ ] **步骤 2：记录但不接管 `MedicalRecordFlowTest` WIP**

读取该文件的 diff，仅用于确认它会影响哪些基线测试。不得执行 `git add`、`git commit`、`git stash`、`git restore` 或任何编辑；原工作区 WIP 的去留和提交时机由用户单独决定。

在实施记录中标记外部阻塞项：

```text
BLOCKED-EXTERNAL: MedicalRecordFlowTest.java 存在用户未提交改动；
治理分支不得接管。全量 verify 仅在用户另行提交/处置后重新同步基线，
否则排除该单测并如实报告“medical-record 全量门禁未满足”。
```

- [ ] **步骤 3：从已提交 HEAD 创建隔离 worktree**

运行：

```bash
git worktree add ../MedConsult-Assist-governance -b review/long-term-governance HEAD
```

预期：新 worktree 工作区干净，不包含原工作区未提交的 `MedicalRecordFlowTest` 改动。不得通过复制文件、补丁或 stash 把 WIP 带入 worktree。

- [ ] **步骤 4：记录基线命令与结果**

运行：

```bash
cd ../MedConsult-Assist-governance
npm --prefix frontend run build
npm --prefix frontend run test:e2e
./backend/mvnw -q -f backend/pom.xml \
  -pl :common-mybatis,:auth-service,:ai-service,:outpatient-service -am \
  -Dtest=MybatisPlusFlowTest,AuthFlowTest,MedicationFunctionServiceTest,RagReadinessServiceTest,OutpatientFlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：前端构建成功并显示现有大 chunk 警告；Playwright 7 条通过；后端前 22 条通过，OutpatientFlowTest 因 H2 `DATE_ADD` 阻塞。将输出摘要写入本计划的执行记录区，不提交日志文件。

- [ ] **步骤 5：建立 review 待办**

每个后续任务创建 5 个固定子项：基线、失败测试、最小实现、2–4 轮 review、文档/commit。

本任务不 commit。

### 任务 1：校准 `修改建议.md` 15 项总览

**文件：**
- 修改：`docs/修改建议.md:12-32`
- 修改：`docs/遗留问题复盘与实施状态.md:1-18`
- 参考：`docs/superpowers/specs/2026-07-17-project-analysis-and-long-term-review-design.md`

- [ ] **步骤 1：逐条填入当前状态，不跳项**

使用以下裁定作为初稿：

| # | 状态 | 必须附的证据 |
| --- | --- | --- |
| 1 | 部分实现，统一“8 个状态/9 个总端点/6 个流转端点”口径 | prescription DDL、Controller、状态机测试；医生开方/患者缴费前端缺口 |
| 2 | 部分实现 | audit_log 表、Consumer；生产 outbox 缺失 |
| 3 | 部分实现 | RBAC DDL/Entity/Mapper 已有；scope 仍为 `*` |
| 4 | 部分实现 | InternalController/Feign 已有；核对 auth 内部端点缺口、用户 JWT 透传与 requireService 冲突 |
| 5 | 部分实现 | RAG 可用；三类规则表仍未接 Mapper |
| 6 | 已实现，文档需校准 | 文本报告/影像分表、Entity、Mapper、消费者；`image-detection`/`imaging-detection` 路径口径 |
| 7 | 部分实现 | sys_service_account 与服务 token 已有；默认 ServiceTokenProvider 与内部服务身份链路测试 |
| 8 | 部分实现 | SSE、锁、限流已落地；审计 MQ 生产端缺失 |
| 9 | 部分实现 | 已修字段保留证据；生成未清零项清单 |
| 10 | 已实现，接口文档和运行依赖需单独验收 | 上传 Controller、MinIO Service、前端调用链 |
| 11 | 部分实现且有契约回归 | 患者端签到调用会被 DOCTOR-only 状态接口拒绝；叫号/复诊仍缺 |
| 12 | 部分实现 | FEFO 已有；退款与检查检验缺失；通用 payment patch 可伪造 REFUNDING/REFUNDED |
| 13 | 未实现 | `id_no` 明文、无 common-crypto、无统一行级过滤 |
| 14 | 部分实现 | 分页默认值冲突仍在；GET body 示例逐项清理 |
| 15 | 部分实现 | 入口角色与写守卫已修；scope、未知 clientType、纯管理员、SELF 过滤、患者签到权限仍缺 |

每个状态后至少给 2 个 `file:line` 证据。

- [ ] **步骤 2：保留历史复盘，不改写 2026-07-16 事实**

在 `docs/遗留问题复盘与实施状态.md` 顶部新增“2026-07-17 状态校准”章节，明确历史段落是旧时点快照。不要把旧结论直接删除。

- [ ] **步骤 3：运行状态完整性检查**

运行：

```bash
python - <<'PY'
from pathlib import Path
text = Path('docs/修改建议.md').read_text(encoding='utf-8')
rows = [line for line in text.splitlines() if line.startswith('| ') and line.split('|')[1].strip().isdigit()]
assert len(rows) == 15, f'问题总览应为 15 条，实际 {len(rows)} 条'
for no in range(1, 16):
    assert any(line.split('|')[1].strip() == str(no) for line in rows), f'缺少 #{no}'
print('修改建议总览 15/15 完整')
PY
```

PowerShell 等价命令：

```powershell
@'
from pathlib import Path
text = Path('docs/修改建议.md').read_text(encoding='utf-8')
rows = [line for line in text.splitlines() if line.startswith('| ') and line.split('|')[1].strip().isdigit()]
assert len(rows) == 15, f'问题总览应为 15 条，实际 {len(rows)} 条'
for no in range(1, 16):
    assert any(line.split('|')[1].strip() == str(no) for line in rows), f'缺少 #{no}'
print('修改建议总览 15/15 完整')
'@ | python -
```

预期：输出 `修改建议总览 15/15 完整`。

- [ ] **步骤 4：执行第 1 轮 docs review**

检查每条状态是否存在“代码已落地但仍写未实现”或“只有未提交测试作为证据”。发现即修正文档。

- [ ] **步骤 5：Commit**

```bash
git status --short
git add docs/修改建议.md docs/遗留问题复盘与实施状态.md
git diff --cached --name-only
git commit -m "docs(review): 校准十五项修改建议实施状态"
```

### 任务 2：建立可机器检查的契约矩阵

**文件：**
- 创建：`docs/全栈契约矩阵.md`
- 创建：`scripts/__init__.py`
- 创建：`scripts/review/__init__.py`
- 创建：`scripts/review/verify_contract_matrix.py`
- 创建：`scripts/review/test_verify_contract_matrix.py`
- 修改：`docs/接口文档.md`
- 修改：`docs/架构设计文档.md`
- 修改：`docs/数据库设计文档.md`

- [ ] **步骤 1：创建包标记并先写检查器失败测试**

创建空文件 `scripts/__init__.py` 与 `scripts/review/__init__.py`，确保从仓库根目录运行 unittest 时可导入检查器。在 `scripts/review/test_verify_contract_matrix.py` 写入：

```python
import tempfile
import unittest
from pathlib import Path

from scripts.review.verify_contract_matrix import parse_matrix, verify_rows


class ContractMatrixVerifierTest(unittest.TestCase):
    def test_reports_missing_docs_frontend_and_backend_paths(self):
        matrix = """| Domain | Method | Public Path | Docs Needle | Frontend | Frontend Needle | Backend | Controller Prefix | Method Suffix | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| auth | POST | /api/v1/auth/login | /auth/login | frontend/src/api/user.js | /auth/login | backend/AuthController.java | /api/v1/auth | /login | aligned |
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "frontend/src/api").mkdir(parents=True)
            (root / "backend").mkdir()
            (root / "docs/接口文档.md").write_text("/auth/register", encoding="utf-8")
            (root / "frontend/src/api/user.js").write_text("/auth/register", encoding="utf-8")
            (root / "backend/AuthController.java").write_text(
                '@RequestMapping("/api/v1/auth")\n@PostMapping("/register")', encoding="utf-8")
            errors = verify_rows(root, parse_matrix(matrix))
        self.assertEqual(3, len(errors))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```bash
python -m unittest scripts/review/test_verify_contract_matrix.py -v
```

预期：FAIL，报 `ModuleNotFoundError` 或缺少 `parse_matrix`。

- [ ] **步骤 3：实现最小检查器**

`verify_contract_matrix.py` 只用 Python 标准库：

```python
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ContractRow:
    domain: str
    method: str
    public_path: str
    docs_needle: str
    frontend: str
    frontend_needle: str
    backend: str
    controller_prefix: str
    method_suffix: str
    table: str
    permission: str
    test: str
    status: str


def parse_matrix(markdown: str) -> list[ContractRow]:
    rows: list[ContractRow] = []
    for line in markdown.splitlines():
        if not line.startswith("|") or "---" in line or "Method" in line:
            continue
        cells = [cell.strip().strip("`") for cell in line.strip("|").split("|")]
        if len(cells) == 13:
            rows.append(ContractRow(*cells))
    return rows


def contains(path: Path, needle: str) -> bool:
    return needle == "—" or (path.exists() and needle in path.read_text(encoding="utf-8", errors="ignore"))


def verify_rows(root: Path, rows: list[ContractRow]) -> list[str]:
    errors: list[str] = []
    docs = root / "docs/接口文档.md"
    for row in rows:
        if row.docs_needle != "—" and not contains(docs, row.docs_needle):
            errors.append(f"{row.domain} {row.method} {row.public_path}: docs 未命中 {row.docs_needle}")
        if row.frontend != "—" and not contains(root / row.frontend, row.frontend_needle):
            errors.append(f"{row.domain} {row.method} {row.public_path}: frontend 未命中 {row.frontend_needle}")
        if row.backend != "—":
            source = root / row.backend
            backend_text = source.read_text(encoding="utf-8", errors="ignore") if source.exists() else ""
            prefix_at = backend_text.find(row.controller_prefix)
            suffix_at = backend_text.find(row.method_suffix, max(prefix_at, 0))
            if prefix_at < 0 or suffix_at < 0:
                errors.append(f"{row.domain} {row.method} {row.public_path}: backend 映射不完整")
            if row.permission != "—" and row.permission not in backend_text:
                errors.append(f"{row.domain} {row.method} {row.public_path}: backend 权限未命中 {row.permission}")
        if row.test != "—" and not (root / row.test).exists():
            errors.append(f"{row.domain} {row.method} {row.public_path}: 缺少测试 {row.test}")
    return errors
```

入口读取 `docs/全栈契约矩阵.md`，有错误时退出码为 1。检查器至少校验 Method、完整 Path、前端调用、后端 method-level mapping、权限注解、数据表和测试文件；不能只靠 Controller 文件里两个字符串存在就判定通过。

- [ ] **步骤 4：运行检查器单测**

运行：

```bash
python -m unittest scripts/review/test_verify_contract_matrix.py -v
```

预期：PASS。

- [ ] **步骤 5：填写全域矩阵**

矩阵至少覆盖 auth、patient、department、doctor、schedule、appointment、medical-record、prescription、drug、notification、attachment、AI、audit。每行填写 Method、完整 Path、前端文件、后端文件、数据表、权限和测试。

AI 域补入以下已实现但文档缺漏项：

- `/api/v1/ai/symptom-chat/session`
- `/api/v1/ai/symptom-chat/history/{sessionId}`
- `/api/v1/files/upload`
- `/api/v1/files/upload/chunk`
- `/api/v1/files/{fileId}`
- `/api/v1/ai/feedback/{feedbackId}/reply`
- `/api/v1/ai/imaging-detection/list`
- report-analysis 与已实现的 SSE 路径
- 前端症状问答必须覆盖 `possibleCauses`、`suggestedDepartments`、`vectorMatches`、`citations` 的展示，不只保存 answer 文本
- 药品创建 DTO、通知枚举、分页字段和默认 pageSize 的仍不一致项

内部 `/internal/**` 单独成表，不与前端契约混在一起。矩阵必须显式标出 `image-detection` 与 `imaging-detection` 的 docs/代码路径冲突，以及 common-feign 用户 JWT 透传和内部 Controller `requireService()` 的身份策略冲突。

- [ ] **步骤 6：统一分页与处方口径**

`docs/接口文档.md` 全局分页默认值统一为 `page=1`、`pageSize=10`、最大 `100`。将处方表述统一为“8 个业务状态、9 个 Controller 端点总数，其中 3 个创建/查询端点、6 个状态流转端点”；若代码发生变化，以状态机测试和 Controller 映射重新计数。

- [ ] **步骤 7：回填数据库与架构事实**

`docs/数据库设计文档.md` 补 prescription、prescription_item、audit_log、RBAC、AI 拆表、文件上传表的当前定义或明确链接到对应 service schema；`docs/架构设计文档.md` 补上传链路和已落地 internal Feign。

- [ ] **步骤 8：运行矩阵检查**

运行：

```bash
python scripts/review/verify_contract_matrix.py
```

预期：所有标记 `aligned` 的前后端路径均命中；docs-only 或 backend-only 行必须使用明确状态，不伪装成一致。

- [ ] **步骤 9：执行第 2 轮契约 review 并 Commit**

```bash
git status --short
git add docs/全栈契约矩阵.md docs/接口文档.md docs/架构设计文档.md docs/数据库设计文档.md \
  scripts/__init__.py scripts/review/__init__.py scripts/review/verify_contract_matrix.py \
  scripts/review/test_verify_contract_matrix.py
git diff --cached --name-only
git commit -m "docs(contract): 建立全栈契约矩阵并校准接口基线"
```

### 任务 3：用真实 MySQL/Redis 修复门诊 FlowTest 地基

**文件：**
- 修改：`backend/outpatient-service/pom.xml`
- 创建：`backend/outpatient-service/src/test/java/com/medconsult/outpatient/support/OutpatientTestContainers.java`
- 创建：`backend/outpatient-service/src/test/resources/schema-mysql-test.sql`
- 修改：`backend/outpatient-service/src/test/java/com/medconsult/outpatient/OutpatientFlowTest.java:20-55`

- [ ] **步骤 1：先保留并复现失败**

运行：

```bash
./backend/mvnw -q -f backend/pom.xml -pl :outpatient-service -am \
  -Dtest=OutpatientFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：6 个 error，根因为 H2 不支持 `DATE_ADD`，没有业务断言被执行。

- [ ] **步骤 2：Docker/Testcontainers preflight**

运行：

```bash
docker version
```

预期：Client 和 Server 均可用。若 Windows Docker Desktop 未启动或无法连接 Docker API，标记 `BLOCKED-ENVIRONMENT`，不得把本任务降级为 H2 测试通过。随后运行一个最小 Testcontainers smoke test，确认 MySQL 8 镜像可拉取、容器可启动、端口可映射。

- [ ] **步骤 3：增加 Testcontainers 依赖**

在 `outpatient-service/pom.xml` 测试依赖区加入：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **步骤 4：创建容器支持类**

```java
package com.medconsult.outpatient.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class OutpatientTestContainers {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("medconsult_outpatient")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.sql.init.schema-locations",
                () -> "classpath:schema-mysql-test.sql");
    }
}
```

- [ ] **步骤 5：准备 MySQL 测试 schema**

不要直接复用 `src/test/resources/schema.sql` 的 H2 方言文件。新建 `src/test/resources/schema-mysql-test.sql`，从生产 `schema.sql` 复制并只保留 MySQL 8 兼容语法；禁止 `CREATE INDEX IF NOT EXISTS` 这类 MySQL 8 不支持写法。测试类通过 `spring.sql.init.schema-locations=classpath:schema-mysql-test.sql` 显式加载。

- [ ] **步骤 6：让 FlowTest 继承支持类并隔离测试种子**

`OutpatientFlowTest` 继承 `OutpatientTestContainers`，删除 H2/Redis 固定地址属性，只保留 JWT、SQL init 和禁用 Nacos 的属性。生产 `schema.sql` 已插入主键 1001–1005 的科室和 2001–2003 的医生，因此测试常量必须改为独立值：

```java
private static final long DEPARTMENT_PK = 91001L;
private static final long DOCTOR_PK = 92001L;
private static final String DEPT_NO = "DEP_TEST";
private static final String DOCTOR_NO = "D_TEST_10001";
private static final LocalDate SCHEDULE_DATE = LocalDate.now().plusDays(2);
```

`seedBaseData()` 使用 `DEPARTMENT_PK`、`DOCTOR_PK`，避免 Testcontainers 执行生产 seed 后发生主键冲突。所有请求体、available 查询和响应断言均使用 `SCHEDULE_DATE.toString()`，替换已过期的固定日期 `2026-07-15`。若测试原来依赖纯数字 doctorNo 解析，应改测试构造逻辑使用真实主键字段，不得继续依赖业务编号可转 Long 的偶然行为。

- [ ] **步骤 7：运行测试验证真实业务断言**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :outpatient-service -am \
  -Dtest=OutpatientFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`Tests run: 6, Failures: 0, Errors: 0`。若失败，按业务断言逐个修复，不得用放宽断言掩盖错误。

- [ ] **步骤 8：执行即时 code review**

检查容器是否复用、测试是否污染本地数据库、并发预约是否仍依赖 Redis 分布式锁、测试结束是否无残留进程。

- [ ] **步骤 9：Commit**

```bash
git status --short
git add backend/outpatient-service/pom.xml \
  backend/outpatient-service/src/test/java/com/medconsult/outpatient/support/OutpatientTestContainers.java \
  backend/outpatient-service/src/test/resources/schema-mysql-test.sql \
  backend/outpatient-service/src/test/java/com/medconsult/outpatient/OutpatientFlowTest.java
git diff --cached --name-only
git commit -m "test(outpatient): 使用 MySQL 和 Redis 容器恢复流程回归"
```

### 任务 4：验证分页自动配置并补性能测量入口

**文件：**
- 修改：`backend/medconsult-common/common-mybatis/src/test/java/com/medconsult/common/mybatis/MybatisPlusFlowTest.java`
- 修改：`backend/auth-service/pom.xml`
- 创建：`backend/auth-service/src/test/java/com/medconsult/auth/support/AuthTestContainers.java`
- 创建：`backend/auth-service/src/test/resources/schema-mysql-test.sql`
- 创建：`backend/auth-service/src/test/java/com/medconsult/auth/AuthPaginationFlowTest.java`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java:500-533`

- [ ] **步骤 1：增加 common-mybatis 分页测试**

插入 5 条名称以 `page_test_` 开头的数据后执行，避免同一 Spring Context 中其他测试插入的数据影响 total：

```java
Page<TestEntity> page = mapper.selectPage(new Page<>(2, 2),
        new QueryWrapper<TestEntity>().likeRight("name", "page_test_").orderByAsc("name"));
assertEquals(5L, page.getTotal());
assertEquals(2, page.getRecords().size());
```

- [ ] **步骤 2：确认 Docker/Testcontainers preflight 已通过**

复用任务 3 的 `docker version` 和最小 Testcontainers smoke test 结果。若环境不可用，标记 `BLOCKED-ENVIRONMENT`，不得退回 H2 验证分页。

- [ ] **步骤 3：为 auth-service 增加真实 MySQL/Redis 容器**

在 `backend/auth-service/pom.xml` 增加：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

创建 `AuthTestContainers`：

```java
package com.medconsult.auth.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AuthTestContainers {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("medconsult_auth")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.sql.init.schema-locations",
                () -> "classpath:schema-mysql-test.sql");
    }
}
```

- [ ] **步骤 4：准备 MySQL 测试 schema**

不要直接复用 `backend/auth-service/src/test/resources/schema.sql` 的 H2 方言文件。新建 `schema-mysql-test.sql`，从生产 schema 复制 MySQL 8 兼容 DDL，保留 RBAC 五表和必要 seed，避免 H2 专用语法污染真实分页测试。

- [ ] **步骤 5：增加 auth-service 自动配置测试**

`AuthPaginationFlowTest` 继承 `AuthTestContainers`，注入 `ApplicationContext` 与 `SysUserMapper`，断言：

```java
assertNotNull(context.getBean(MybatisPlusInterceptor.class));
Page<SysUser> result = userMapper.selectPage(new Page<>(2, 10),
        new LambdaQueryWrapper<SysUser>().likeRight(SysUser::getAccount, "page_test_")
                .orderByAsc(SysUser::getId));
assertEquals(10, result.getRecords().size());
assertEquals(25L, result.getTotal());
```

测试 `@BeforeEach` 插入 25 个 `page_test_` 前缀唯一账号，避免生产 schema seed 影响 total。真实 MySQL 日志或 datasource-proxy 必须确认 SQL 含 `LIMIT`。

- [ ] **步骤 6：运行测试确认真实现状**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :common-mybatis,:auth-service -am \
  -Dtest=MybatisPlusFlowTest,AuthPaginationFlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：分页 SQL 包含 `LIMIT`，第 2 页返回 10 条。若 auth 测试失败，修复 `MedConsultMybatisAutoConfiguration` 的加载顺序或 auth 依赖；不得继续保留内存分页。

- [ ] **步骤 7：删除过时注释并恢复 DB 分页骨架**

先将 `selectList + subList` 改为 `selectPage`。角色过滤仍由任务 6 的 RBAC JOIN 完成；在该任务完成前，角色条件为空时必须已经使用 DB 分页。

- [ ] **步骤 8：验证并 Commit**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :auth-service -am test
git status --short
git add backend/medconsult-common/common-mybatis/src/test/java/com/medconsult/common/mybatis/MybatisPlusFlowTest.java \
  backend/auth-service/pom.xml \
  backend/auth-service/src/test/java/com/medconsult/auth/support/AuthTestContainers.java \
  backend/auth-service/src/test/resources/schema-mysql-test.sql \
  backend/auth-service/src/test/java/com/medconsult/auth/AuthPaginationFlowTest.java \
  backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java
git diff --cached --name-only
git commit -m "perf(auth): 恢复用户列表数据库分页基础"
```

---

## 四、第 3–4 周：权限、敏感数据与审计闭环

### 任务 5：校准并复用已有架构遗留计划

**文件：**
- 修改：`docs/superpowers/plans/2026-07-16-legacy-arch-issues.md`
- 修改：`docs/superpowers/plans/2026-07-17-security-login-entry-and-api-authz.md`

- [ ] **步骤 1：逐任务核对已完成文件**

将 RBAC DDL/Entity/Mapper、登录 `clientType`、Controller 写守卫等已有步骤标记完成；把仍缺失的 RbacQueryService、scope 驱动、审计生产端、规则 Mapper、id_no 加密和退款保留为未完成。

- [ ] **步骤 2：修正两个高风险旧方案**

1. 审计 outbox 必须在业务事务内同步插入 `local_message`，不能在 `AFTER_COMMIT` 后再插入，否则进程在业务提交与消息插入之间崩溃会丢审计。
2. `id_no` 迁移不能只靠 SQL 调用应用密钥；改为有配置开关的 Java 批量迁移器，执行双写、校验和可重入迁移。

- [ ] **步骤 3：运行计划一致性扫描**

```bash
rg -n "AFTER_COMMIT|V1__encrypt_id_no.sql|sys_role 等 4 张.*完全缺失" docs/superpowers/plans docs/遗留问题复盘与实施状态.md
```

预期：旧历史证据可保留在复盘时点说明中；当前实施步骤不再要求非原子的 AFTER_COMMIT outbox 或纯 SQL 应用层加密。

- [ ] **步骤 4：Commit**

```bash
git status --short
git add docs/superpowers/plans/2026-07-16-legacy-arch-issues.md docs/superpowers/plans/2026-07-17-security-login-entry-and-api-authz.md
git diff --cached --name-only
git commit -m "docs(plan): 按当前实现校准安全与遗留任务"
```

### 任务 6：让 RBAC 五表驱动角色、scope 和用户分页

**文件：**
- 创建：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/RbacQueryService.java`
- 创建：`backend/auth-service/src/test/java/com/medconsult/auth/user/service/RbacQueryServiceTest.java`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/mapper/SysUserMapper.java`
- 创建：`backend/auth-service/src/main/resources/mapper/SysUserMapper.xml`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/DataSeeder.java`
- 修改：`backend/auth-service/src/main/resources/application.yml`
- 修改：`backend/auth-service/src/test/java/com/medconsult/auth/AuthFlowTest.java`

- [ ] **步骤 1：先写 RBAC 查询失败测试**

覆盖以下行为：

```java
assertEquals(List.of("patient:read"), service.resolveScope(userId, "PATIENT"));
assertEquals("HOSPITAL_ADMIN", service.resolvePrimaryRole(adminUserId));
assertEquals(List.of("*"), service.resolveScope(noPermissionUserId, "PATIENT")); // 仅灰度开关=true
assertThrows(BusinessException.class,
        () -> strictService.resolveScope(noPermissionUserId, "PATIENT"));
```

- [ ] **步骤 2：实现批量查询接口**

`RbacQueryService` 暴露：

```java
String resolvePrimaryRole(Long userId);
Map<Long, String> resolvePrimaryRoles(Collection<Long> userIds);
List<String> resolveScope(Long userId, String roleCode);
```

角色从 `sys_user_role JOIN sys_role` 查询；scope 从 `sys_role_permission JOIN sys_permission` 查询。Redis 仅作缓存，DB 是事实源。

- [ ] **步骤 3：增加灰度配置**

```yaml
medconsult:
  security:
    rbac:
      allow-wildcard-fallback: true
```

本地默认兼容旧行为；生产配置明确设为 `false`。缓存 key 包含角色与权限版本，TTL 为 30 分钟。

- [ ] **步骤 4：改登录、刷新与绑定**

把 `AuthServiceImpl.java` 中两处 `List.of("*")` 改为 `rbacQueryService.resolveScope`；角色判断不再仅依赖关联 ID 或 Redis。纯 `HOSPITAL_ADMIN`（三关联 ID 均空）按 RBAC 角色归入 STAFF。

- [ ] **步骤 5：把 listUsers 变为 DB 分页 + 批量角色回填**

无 role 条件时 `selectPage`；有 role 条件时在 `SysUserMapper` 增加 `selectPageByRole(Page<SysUser> page, String roleCode, String keyword)`，XML 使用 `sys_user JOIN sys_user_role JOIN sys_role` 并保留逻辑删除条件。当前页角色通过 `resolvePrimaryRoles(Collection<Long>)` 一次批量回填，禁止逐用户 `resolveRole()`。目标：单请求 SQL 不超过 3 次（count、page、批量角色），Redis 调用不随总用户 U 增长。

- [ ] **步骤 6：运行测试**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :auth-service -am test
```

预期：AuthFlowTest、AuthPaginationFlowTest、RbacQueryServiceTest 全部通过；患者从 STAFF 拒绝，纯管理员从 STAFF 成功，strict 模式空权限拒绝签发全权限 token。

- [ ] **步骤 7：性能基线**

插入 10,000 个用户，记录 pageSize=20 时 SQL 数、Redis 调用数和 P95。退出条件：数据库只返回 20 条记录，远程/缓存调用为 O(pageSize) 或 O(1)，不再 O(U)。

- [ ] **步骤 8：3 轮 review 与 Commit**

第 1 轮安全/角色，第 2 轮分页/缓存，第 3 轮降级/兼容性。

```bash
git status --short
git add backend/auth-service/src/main/java/com/medconsult/auth/user/service/RbacQueryService.java \
  backend/auth-service/src/test/java/com/medconsult/auth/user/service/RbacQueryServiceTest.java \
  backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java \
  backend/auth-service/src/main/java/com/medconsult/auth/user/mapper/SysUserMapper.java \
  backend/auth-service/src/main/resources/mapper/SysUserMapper.xml \
  backend/auth-service/src/main/java/com/medconsult/auth/DataSeeder.java \
  backend/auth-service/src/main/resources/application.yml \
  backend/auth-service/src/test/java/com/medconsult/auth/AuthFlowTest.java
git diff --cached --name-only
git commit -m "feat(auth): 使用 RBAC 驱动角色权限与用户分页"
```

### 任务 7：用显式 SQL 条件闭合 SELF/ASSIGNED 数据范围

**文件：**
- 创建：`backend/medical-record-service/src/test/java/com/medconsult/medicalrecord/MedicalRecordAuthorizationTest.java`
- 创建：`backend/outpatient-service/src/test/java/com/medconsult/outpatient/AppointmentAuthorizationTest.java`
- 创建：`frontend/e2e/appointment-checkin.spec.ts`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/medicalrecord/service/MedicalRecordServiceImpl.java`
- 修改：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/controller/AppointmentController.java`
- 修改：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java`
- 修改：`frontend/src/api/appointment.js`
- 修改：`frontend/src/views/patient/appointment/MyAppointment.vue`
- 修改：`docs/架构设计文档.md`

- [ ] **步骤 1：写越权失败测试**

分别覆盖：患者只能查自己的预约/病历；患者可对本人 BOOKED/PAID 预约执行签到；医生只能把已签到预约推进 IN_PROGRESS/COMPLETED；医生只能查本人接诊或被分配患者；管理员可按授权范围查；直接传其他 userId 不得绕过 token owner。

- [ ] **步骤 2：运行测试确认至少一个越权分支失败**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :medical-record-service,:outpatient-service -am \
  -Dtest=MedicalRecordAuthorizationTest,AppointmentAuthorizationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **步骤 3：在 Service 查询条件中下沉身份约束**

不要引入隐式全局 SQL 魔法。根据 `SecurityContext.requireUser()` 的 patientId/doctorId/role，显式追加 `patient_id = ?`、`doctor_id = ?` 或 appointment 关联条件；请求参数只能进一步收窄，不能放宽 token 范围。签到必须使用患者 SELF 范围或独立 patient-checkin 动作；叫号/完成仍只能由医生执行。

- [ ] **步骤 4：验证 4 类安全用例**

正向、未认证、跨角色、跨 owner 全部通过。额外验证患者签到不再 403，患者不能直接推进 IN_PROGRESS/COMPLETED，医生不能替非本人接诊预约推进状态。检查日志不输出证件号、token 或完整病历内容。

- [ ] **步骤 5：更新架构说明并 Commit**

```bash
git status --short
git add backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/medicalrecord/service/MedicalRecordServiceImpl.java \
  backend/medical-record-service/src/test/java/com/medconsult/medicalrecord/MedicalRecordAuthorizationTest.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/controller/AppointmentController.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java \
  backend/outpatient-service/src/test/java/com/medconsult/outpatient/AppointmentAuthorizationTest.java \
  frontend/src/api/appointment.js frontend/src/views/patient/appointment/MyAppointment.vue \
  frontend/e2e/appointment-checkin.spec.ts \
  docs/架构设计文档.md
git diff --cached --name-only
git commit -m "fix(security): 下沉预约与病历行级数据范围"
```

### 任务 8：实现事务内审计 outbox

**文件：**
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/LocalMessageOutboxService.java`
- 创建：`backend/medconsult-common/common-mq/src/test/java/com/medconsult/common/mq/LocalMessageOutboxServiceTest.java`
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditLogEvent.java`
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditOutboxService.java`
- 创建：`backend/medconsult-common/common-mq/src/test/java/com/medconsult/common/mq/AuditOutboxServiceTest.java`
- 修改：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MedConsultMqAutoConfiguration.java`
- 修改：`backend/auth-service/pom.xml`、`backend/patient-service/pom.xml`、`backend/outpatient-service/pom.xml`、`backend/drug-service/pom.xml`、`backend/medical-record-service/pom.xml`
- 修改：`backend/auth-service/src/main/resources/{schema.sql,application.yml}`、`backend/patient-service/src/main/resources/{schema.sql,application.yml}`、`backend/outpatient-service/src/main/resources/{schema.sql,application.yml}`、`backend/drug-service/src/main/resources/{schema.sql,application.yml}`、`backend/medical-record-service/src/main/resources/{schema.sql,application.yml}`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/AuthServiceApplication.java`、`backend/patient-service/src/main/java/com/medconsult/patient/PatientServiceApplication.java`、`backend/outpatient-service/src/main/java/com/medconsult/outpatient/OutpatientServiceApplication.java`、`backend/drug-service/src/main/java/com/medconsult/drug/DrugServiceApplication.java`、`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/MedicalRecordServiceApplication.java`，把 `com.medconsult.common.mq` 加入 `@MapperScan`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java`、`backend/patient-service/src/main/java/com/medconsult/patient/service/PatientServiceImpl.java`、`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentTxService.java`、`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/medicalrecord/service/MedicalRecordServiceImpl.java`、`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionTxService.java`、`backend/drug-service/src/main/java/com/medconsult/drug/service/DrugStockTxService.java`
- 修改：`backend/notification-service/src/main/java/com/medconsult/notification/consumer/AuditLogConsumer.java`
- 删除：`backend/notification-service/src/main/java/com/medconsult/notification/consumer/AuditLogEvent.java`

- [ ] **步骤 1：写事务原子性测试**

测试业务事务成功时业务行与 local_message 同时存在；业务事务抛异常时两者都不存在；messageNo 重复时只保留一条。

- [ ] **步骤 2：实现通用事务内 outbox 与审计适配**

`LocalMessageOutboxService` 提供唯一的可靠入队实现：

```java
@Transactional(propagation = Propagation.MANDATORY)
public void enqueue(String exchange, String messageNo, String routingKey, Object event) {
    String payload = objectMapper.writeValueAsString(event);
    localMessageMapper.insert(LocalMessage.of(exchange, messageNo, routingKey, payload));
}
```

`AuditOutboxService.enqueue(AuditLogEvent)` 只负责选择 `EXCHANGE_LOG`、`RK_AUDIT_LOG`，并委托通用 outbox。使用 `MANDATORY` 强制调用者处于业务事务，避免审计与业务分开提交；任务 13 的调剂命令/结果也必须复用该服务，不重复插表逻辑。

- [ ] **步骤 3：在关键写 Service 显式调用**

首批覆盖登录/改密、患者档案更新、预约状态、病历创建/归档、处方审方/调剂、药品入出库。事件只记录业务编号、动作、operator 和必要变更摘要，不记录密码、token、证件号和完整病历。

- [ ] **步骤 4：配置 publisher confirm**

所有生产 outbox 的服务设置：

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
```

并确保各库有 `local_message` 及 `(status, next_retry_at)` 索引。

- [ ] **步骤 5：消费端改用共享事件**

删除 notification-service 内部重复 `AuditLogEvent`，消费者使用 common-mq 类型，保留 messageNo 幂等。

- [ ] **步骤 6：故障注入验证**

RabbitMQ 关闭时业务事务仍成功、消息停留 PENDING/SENT；恢复后推进 CONFIRMED 并写 audit_log。重复投递不得产生重复 audit_log。

- [ ] **步骤 7：3 轮 review 与 Commit**

```bash
git status --short
git add backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/LocalMessageOutboxService.java \
  backend/medconsult-common/common-mq/src/test/java/com/medconsult/common/mq/LocalMessageOutboxServiceTest.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditLogEvent.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditOutboxService.java \
  backend/medconsult-common/common-mq/src/test/java/com/medconsult/common/mq/AuditOutboxServiceTest.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MedConsultMqAutoConfiguration.java \
  backend/{auth-service,patient-service,outpatient-service,drug-service,medical-record-service}/pom.xml \
  backend/{auth-service,patient-service,outpatient-service,drug-service,medical-record-service}/src/main/resources/schema.sql \
  backend/{auth-service,patient-service,outpatient-service,drug-service,medical-record-service}/src/main/resources/application.yml \
  backend/auth-service/src/main/java/com/medconsult/auth/AuthServiceApplication.java \
  backend/patient-service/src/main/java/com/medconsult/patient/PatientServiceApplication.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/OutpatientServiceApplication.java \
  backend/drug-service/src/main/java/com/medconsult/drug/DrugServiceApplication.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/MedicalRecordServiceApplication.java \
  backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java \
  backend/patient-service/src/main/java/com/medconsult/patient/service/PatientServiceImpl.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentTxService.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/medicalrecord/service/MedicalRecordServiceImpl.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionTxService.java \
  backend/drug-service/src/main/java/com/medconsult/drug/service/DrugStockTxService.java \
  backend/notification-service/src/main/java/com/medconsult/notification/consumer/AuditLogConsumer.java \
  backend/notification-service/src/main/java/com/medconsult/notification/consumer/AuditLogEvent.java
git diff --cached --name-only
git commit -m "feat(audit): 使用事务内 outbox 闭合审计生产链路"
```

### 任务 9：证件号双写加密与可重入迁移

**文件：**
- 创建：`backend/medconsult-common/common-crypto/pom.xml`
- 创建：`backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/CryptoAutoConfiguration.java`
- 创建：`backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/AesGcmCryptoService.java`
- 创建：`backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/Sha256HashService.java`
- 创建：`backend/medconsult-common/common-crypto/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 创建：`backend/medconsult-common/common-crypto/src/test/java/com/medconsult/common/crypto/AesGcmCryptoServiceTest.java`
- 创建：`backend/medconsult-common/common-crypto/src/test/java/com/medconsult/common/crypto/Sha256HashServiceTest.java`
- 修改：`backend/pom.xml`
- 修改：`backend/medconsult-common/pom.xml`
- 修改：`backend/patient-service/pom.xml`
- 修改：`backend/patient-service/src/main/java/com/medconsult/patient/entity/Patient.java`
- 修改：`backend/patient-service/src/main/java/com/medconsult/patient/service/PatientServiceImpl.java`
- 创建：`backend/patient-service/src/main/java/com/medconsult/patient/service/IdNoMigrationRunner.java`
- 修改：`backend/patient-service/src/main/resources/schema.sql`
- 修改：`backend/patient-service/src/main/resources/application.yml`
- 创建：`backend/patient-service/src/test/java/com/medconsult/patient/PatientIdNoEncryptionTest.java`
- 创建：`backend/patient-service/src/test/java/com/medconsult/patient/IdNoMigrationRunnerTest.java`

- [ ] **步骤 1：写加密、盲索引和错误密钥测试**

断言 AES-256-GCM 同一明文两次密文不同、均可解密；SHA-256 盲索引稳定；错误密钥解密失败；日志不包含明文。

- [ ] **步骤 2：创建 common-crypto**

提供 `AesGcmCryptoService.encrypt/decrypt` 与 `Sha256HashService.hashNormalizedIdNo`。`backend/medconsult-common/pom.xml` 增加 `<module>common-crypto</module>`；`backend/pom.xml` 的 dependencyManagement 注册 `com.medconsult:common-crypto:${project.version}`；`patient-service/pom.xml` 引入该模块。密钥只从环境/Nacos 读取，启动时校验 Base64 解码后为 32 bytes；未配置时 patient-service 必须启动失败，不允许回退硬编码密钥。

- [ ] **步骤 3：DDL 增加双写列**

新增 `id_no_ciphertext TEXT`、`id_no_hash CHAR(64)` 和联合唯一索引 `uk_patient_id_type_hash(id_type, id_no_hash)`；删除旧 `uk_patient_id_card(id_type, id_no)`。旧 `id_no` 仅作为存量迁移输入和短期回读兜底，新请求不得再写明文。生产 seed 不再在 `schema.sql` 写固定证件号；测试需要的明文存量由迁移测试在隔离数据库显式插入。

- [ ] **步骤 4：Service 双写与按 hash 查询**

创建/更新时只写 ciphertext + hash，不再写旧 `id_no`；hash 输入为 `idType + ':' + 规范化证件号`，唯一性和精确查询使用 `id_type + id_no_hash`；列表 keyword 不再对证件号执行 `LIKE`。迁移期读取优先解密 ciphertext，只有 ciphertext 为空才读取旧明文并立即安排迁移；响应 DTO 继续只返回 `idNoMasked`。

- [ ] **步骤 5：实现批量迁移器**

按主键游标每批 200 条扫描 `id_no_hash IS NULL AND id_no IS NOT NULL`，在事务内写密文/hash；重复运行只处理未迁移行。迁移由 `medconsult.crypto.migration-enabled=false` 显式开关控制。全量校验通过并完成备份后，再以独立运维步骤分批把已迁移行的旧 `id_no` 置 NULL；本任务不删除旧列，保留版本回滚能力。

- [ ] **步骤 6：迁移验证**

迁移前后总行数一致、hash 非空数量等于旧明文数量、随机抽样解密一致、重复运行更新 0 行。

- [ ] **步骤 7：4 轮 review 与 Commit**

第 1 轮密码学，第 2 轮查询/兼容，第 3 轮迁移/回滚，第 4 轮日志和敏感信息。

```bash
git status --short
git add backend/pom.xml backend/medconsult-common/pom.xml \
  backend/medconsult-common/common-crypto/pom.xml \
  backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/CryptoAutoConfiguration.java \
  backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/AesGcmCryptoService.java \
  backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/Sha256HashService.java \
  backend/medconsult-common/common-crypto/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
  backend/medconsult-common/common-crypto/src/test/java/com/medconsult/common/crypto/AesGcmCryptoServiceTest.java \
  backend/medconsult-common/common-crypto/src/test/java/com/medconsult/common/crypto/Sha256HashServiceTest.java \
  backend/patient-service/pom.xml \
  backend/patient-service/src/main/java/com/medconsult/patient/entity/Patient.java \
  backend/patient-service/src/main/java/com/medconsult/patient/service/PatientServiceImpl.java \
  backend/patient-service/src/main/java/com/medconsult/patient/service/IdNoMigrationRunner.java \
  backend/patient-service/src/main/resources/schema.sql \
  backend/patient-service/src/main/resources/application.yml \
  backend/patient-service/src/test/java/com/medconsult/patient/PatientIdNoEncryptionTest.java \
  backend/patient-service/src/test/java/com/medconsult/patient/IdNoMigrationRunnerTest.java
git diff --cached --name-only
git commit -m "feat(patient): 双写加密证件号并提供可重入迁移"
```

---

## 五、第 5–6 周：业务闭环与复杂度治理

### 任务 10：接入症状规则表并保留零漂移降级

**文件：**
- 创建：`backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/SymptomRuleEntity.java`
- 创建：`backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/HighRiskSymptomRuleEntity.java`
- 创建：`backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/NegativeRuleEntity.java`
- 创建：`backend/ai-service/src/main/java/com/medconsult/ai/persistence/mapper/SymptomRuleMapper.java`
- 创建：`backend/ai-service/src/main/java/com/medconsult/ai/persistence/mapper/HighRiskSymptomRuleMapper.java`
- 创建：`backend/ai-service/src/main/java/com/medconsult/ai/persistence/mapper/NegativeRuleMapper.java`
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/knowledge/RiskRuleEngine.java`
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/service/SymptomChatService.java`
- 创建：`backend/ai-service/src/test/java/com/medconsult/ai/knowledge/RiskRuleEngineDatabaseTest.java`

- [ ] **步骤 1：把当前硬编码行为固化为参数化测试**

覆盖 CRITICAL、MEDIUM、LOW、否定词、空库和数据库异常。

- [ ] **步骤 2：实现 Mapper 查询与缓存**

按启用规则加载，编译为不可变内存快照；刷新失败保留上一份快照。库为空时使用当前常量，返回结果逐字段与改造前一致。

- [ ] **步骤 3：改为 Spring 注入**

`RiskRuleEngine` 标记 `@Component`，`SymptomChatService` 使用构造器注入，不再 `new`。

- [ ] **步骤 4：运行 AI 测试**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :ai-service -am test
```

预期：规则测试、RAG readiness 和 symptom-chat 相关测试全部通过。

- [ ] **步骤 5：Commit**

```bash
git status --short
git add backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/SymptomRuleEntity.java \
  backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/HighRiskSymptomRuleEntity.java \
  backend/ai-service/src/main/java/com/medconsult/ai/persistence/entity/NegativeRuleEntity.java \
  backend/ai-service/src/main/java/com/medconsult/ai/persistence/mapper/SymptomRuleMapper.java \
  backend/ai-service/src/main/java/com/medconsult/ai/persistence/mapper/HighRiskSymptomRuleMapper.java \
  backend/ai-service/src/main/java/com/medconsult/ai/persistence/mapper/NegativeRuleMapper.java \
  backend/ai-service/src/main/java/com/medconsult/ai/knowledge/RiskRuleEngine.java \
  backend/ai-service/src/main/java/com/medconsult/ai/service/SymptomChatService.java \
  backend/ai-service/src/test/java/com/medconsult/ai/knowledge/RiskRuleEngineDatabaseTest.java
git diff --cached --name-only
git commit -m "feat(ai-service): 接入症状规则表并保留硬编码降级"
```

### 任务 11：实现退款单状态机并消除误导文案

**文件：**
- 修改：`backend/outpatient-service/src/main/resources/schema.sql`
- 修改：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/dto/AppointmentDTO.java`
- 修改：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java`
- 修改：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/controller/AppointmentController.java`
- 创建：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/entity/RefundOrder.java`
- 创建：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/mapper/RefundOrderMapper.java`
- 创建：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/dto/RefundDTO.java`
- 创建：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/service/RefundService.java`
- 创建：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/service/RefundServiceImpl.java`
- 创建：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/controller/RefundController.java`
- 修改：`frontend/src/api/appointment.js`
- 修改：`frontend/src/mock/appointment.js`
- 修改：`frontend/src/views/patient/appointment/MyAppointment.vue`
- 创建：`backend/outpatient-service/src/test/java/com/medconsult/outpatient/RefundFlowTest.java`
- 创建：`frontend/e2e/refund.spec.ts`
- 修改：`docs/接口文档.md`、`docs/数据库设计文档.md`

- [ ] **步骤 1：先写支付/退款失败测试**

覆盖：普通患者不能通过 `PATCH /appointments/{id}/payment` 直接把状态改成 REFUNDING/REFUNDED；PAID 预约申请退款；重复请求幂等；UNPAID 拒绝；REFUNDED 重复返回原退款单；并发请求只有一张 refund_order。

- [ ] **步骤 2：新增 refund_order**

状态为 `REQUESTED → PROCESSING → SUCCEEDED/FAILED`，`appointment_id` 唯一；保存退款编号、金额、渠道、幂等键、失败原因和时间。

- [ ] **步骤 3：实现接口**

`POST /api/v1/appointments/{appointmentId}/refund` 使用 Redis 锁 + DB 唯一约束双保险。无真实支付网关时只实现 `MOCK` provider，并在接口响应明确 `provider=MOCK`，前端不得声称已原路到账。

同时收紧 `updatePayment` 白名单：普通用户仅允许完成真实支付状态所需的 `UNPAID → PAID`，退款相关状态只能由 RefundService 内部流转，不能由通用 payment patch 直接写入。

- [ ] **步骤 4：实现前端入口和 E2E**

只有 PAID 且未退款的预约显示“申请退款”；显示处理中/成功/失败状态。mock E2E 验证重复点击不会创建两笔退款。

- [ ] **步骤 5：更新契约并验证**

```bash
./backend/mvnw -q -f backend/pom.xml -pl :outpatient-service -am test
npm --prefix frontend run test:e2e -- e2e/refund.spec.ts
```

- [ ] **步骤 6：3 轮 review 与 Commit**

```bash
git status --short
git add backend/outpatient-service/src/main/resources/schema.sql \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/dto/AppointmentDTO.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/controller/AppointmentController.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/entity/RefundOrder.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/mapper/RefundOrderMapper.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/dto/RefundDTO.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/service/RefundService.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/service/RefundServiceImpl.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/controller/RefundController.java \
  backend/outpatient-service/src/test/java/com/medconsult/outpatient/RefundFlowTest.java \
  frontend/src/api/appointment.js frontend/src/mock/appointment.js \
  frontend/src/views/patient/appointment/MyAppointment.vue frontend/e2e/refund.spec.ts \
  docs/接口文档.md docs/数据库设计文档.md
git diff --cached --name-only
git commit -m "feat(appointment): 补齐幂等退款状态机与患者入口"
```

### 任务 12：批量查询药品风险，消除用药分析 N+1

**文件：**
- 修改：`docs/架构设计文档.md`
- 修改：`backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/client/DrugFeignClient.java`
- 创建：`backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/dto/DrugRiskBatchRequest.java`
- 创建：`backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/dto/DrugRiskBatchResponse.java`
- 修改：`backend/drug-service/src/main/java/com/medconsult/drug/controller/DrugInternalController.java`
- 修改：`backend/drug-service/src/main/java/com/medconsult/drug/service/DrugService.java`
- 修改：`backend/drug-service/src/main/java/com/medconsult/drug/service/DrugServiceImpl.java`
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/service/MedicationFunctionService.java`
- 修改：`backend/ai-service/src/test/java/com/medconsult/ai/service/MedicationFunctionServiceTest.java`
- 修改：`backend/drug-service/src/test/java/com/medconsult/drug/DrugFlowTest.java`
- 修改：`frontend/src/api/ai.js`
- 修改：`frontend/src/views/doctor/ai-tool/MedicationAnalysis.vue`
- 创建：`frontend/e2e/medication-analysis-real-data.spec.ts`

- [ ] **步骤 1：先用 Mockito 验证当前 N 次调用**

构造 10 个唯一 drugId，断言当前 `getRiskInfo` 调用 10 次。该测试作为性能回归的失败基线。

- [ ] **步骤 2：先更新 internal 架构契约**

增加：

```text
POST /internal/drugs/risk-info/batch
body: { "drugIds": [1,2,3] }
response: { "items": [{ "drugId": 1, "genericName": "阿司匹林", "contraindications": [], "interactions": [] }], "missingDrugIds": [] }
限制：1–100 个去重主键
```

定义共享类型：

```java
public record DrugRiskBatchRequest(List<Long> drugIds) {}

public record DrugRiskBatchResponse(
        List<DrugRiskInfoDTO> items,
        List<Long> missingDrugIds
) {}
```

`DrugFeignClient` 增加：

```java
@PostMapping("/internal/drugs/risk-info/batch")
Result<DrugRiskBatchResponse> getRiskInfoBatch(@RequestBody DrugRiskBatchRequest request);
```

这是 internal 性能契约，不暴露到 Gateway。

- [ ] **步骤 3：实现单次 SQL 批量查询**

Service 对去重 ID 使用 `selectBatchIds`，按输入顺序返回结果；缺失 ID 单独列出。Controller 强制 `SecurityContext.requireService()`。

- [ ] **步骤 4：AI 侧改为 1 次 Feign**

`MedicationFunctionService` 保留本地规则降级，但 functionTrace 按药品分别记录命中/缺失，不能因批量化丢失可解释性。

- [ ] **步骤 5：前端移除 placeholder 假成功**

`MedicationAnalysis.vue` 和 `frontend/src/api/ai.js` 不得再构造 `drugName: 'placeholder'`。页面必须从真实处方详情或用户选择的药品列表提交 `drugIds/drugNames`；缺少真实药品数据时显示错误并阻止请求。E2E 覆盖“无药品时不请求后端”和“真实处方药品触发分析”。

- [ ] **步骤 6：验证复杂度**

```java
verify(drugClient, times(1)).getRiskInfoBatch(any());
verify(drugClient, never()).getRiskInfo(anyLong());
```

10 个药品目标为 1 次 Feign + 1 次 SQL，时间复杂度 O(D)，跨服务 RTT 从 O(D) 降为 O(1)。

- [ ] **步骤 7：Commit**

```bash
git status --short
git add docs/架构设计文档.md \
  backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/client/DrugFeignClient.java \
  backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/dto/DrugRiskBatchRequest.java \
  backend/medconsult-common/common-feign/src/main/java/com/medconsult/common/feign/dto/DrugRiskBatchResponse.java \
  backend/drug-service/src/main/java/com/medconsult/drug/controller/DrugInternalController.java \
  backend/drug-service/src/main/java/com/medconsult/drug/service/DrugService.java \
  backend/drug-service/src/main/java/com/medconsult/drug/service/DrugServiceImpl.java \
  backend/drug-service/src/test/java/com/medconsult/drug/DrugFlowTest.java \
  backend/ai-service/src/main/java/com/medconsult/ai/service/MedicationFunctionService.java \
  backend/ai-service/src/test/java/com/medconsult/ai/service/MedicationFunctionServiceTest.java \
  frontend/src/api/ai.js frontend/src/views/doctor/ai-tool/MedicationAnalysis.vue \
  frontend/e2e/medication-analysis-real-data.spec.ts
git diff --cached --name-only
git commit -m "perf(ai-service): 批量获取药品风险消除跨服务 N+1"
```

### 任务 13：把处方调剂改为可观测异步状态机

**文件：**
- 修改：`docs/架构设计文档.md`、`docs/接口文档.md`、`docs/数据库设计文档.md`
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/PrescriptionDispenseCommand.java`
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/PrescriptionDispenseResult.java`
- 修改：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MqConstants.java`
- 修改：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MedConsultMqAutoConfiguration.java`
- 修改：`backend/medical-record-service/src/main/resources/schema.sql`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/entity/Prescription.java`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/dto/PrescriptionDTO.java`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/controller/PrescriptionController.java`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionService.java`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionServiceImpl.java`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionTxService.java`
- 创建：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/consumer/DispenseResultConsumer.java`
- 创建：`backend/medical-record-service/src/test/java/com/medconsult/medicalrecord/prescription/PrescriptionDispenseStateMachineTest.java`
- 修改：`backend/drug-service/src/main/resources/schema.sql`
- 创建：`backend/drug-service/src/main/java/com/medconsult/drug/entity/DispenseCommandRecord.java`
- 创建：`backend/drug-service/src/main/java/com/medconsult/drug/mapper/DispenseCommandRecordMapper.java`
- 创建：`backend/drug-service/src/main/java/com/medconsult/drug/service/DispenseCommandService.java`
- 创建：`backend/drug-service/src/main/java/com/medconsult/drug/consumer/DispenseCommandConsumer.java`
- 修改：`backend/drug-service/src/main/java/com/medconsult/drug/service/DrugStockTxService.java`
- 创建：`backend/drug-service/src/test/java/com/medconsult/drug/DispenseCommandConsumerTest.java`
- 修改：`frontend/src/api/prescription.js`
- 创建：`frontend/src/mock/prescription.js`
- 修改：`frontend/src/views/doctor/record/RecordWrite.vue`
- 创建：`frontend/src/views/patient/prescription/PrescriptionList.vue`
- 修改：`frontend/src/router/modules/patient.js`
- 修改：`frontend/src/views/pharmacy/prescription/PrescriptionReview.vue`
- 创建：`frontend/e2e/prescription-full-flow.spec.ts`
- 创建：`frontend/e2e/prescription-dispense.spec.ts`

- [ ] **步骤 1：先闭合处方前端用户流**

补医生结构化开方和提交审方入口，补患者本人处方列表/详情/缴费入口。E2E 覆盖医生开方→提交→药师审方→患者缴费→药师发药，不再用“病历草稿保存”替代处方链路。

- [ ] **步骤 2：固化当前同步链路基线**

对 1、5、10、20 条明细注入 drug-service 200 ms 延迟，记录接口耗时、锁持有时间、Feign 次数、失败补偿次数和库存差异。

- [ ] **步骤 3：文档先定义状态机**

增加 `DISPENSING` 与 `DISPENSE_FAILED`：

```text
APPROVED/PAID → DISPENSING → DISPENSED
                         ↘ DISPENSE_FAILED → 可人工重试
```

`POST /dispense` 返回已受理状态，不再同步等待所有库存扣减。

- [ ] **步骤 4：写状态机失败测试**

覆盖重复命令、部分库存不足、消费者重投、结果事件重复、进程重启和人工重试。

- [ ] **步骤 5：在 medical-record 本地事务写状态 + outbox**

锁内将处方改为 DISPENSING，并插入唯一 messageNo 的调剂命令。同步接口目标 P95 不受明细数 N 线性影响。

- [ ] **步骤 6：drug-service 原子消费**

按处方维度幂等；按 drugNo 排序获取锁，单个本地事务完成所有 FEFO 扣减。任一明细失败则本地事务回滚，发送失败结果，不再依赖逐条跨服务补偿。

- [ ] **步骤 7：结果消费者推进终态**

成功改 DISPENSED，失败改 DISPENSE_FAILED 并记录结构化原因。前端轮询/刷新后展示处理中和失败重试，不显示假成功。

- [ ] **步骤 8：故障注入与性能验收**

MQ 关闭、消费者崩溃、重复消息、库存不足场景均无重复扣减。10 条明细同步受理耗时相对旧链路至少不随 10×下游延迟增长；最终完成耗时单独展示。

- [ ] **步骤 9：4 轮 review 与 Commit**

```bash
git status --short
git add docs/架构设计文档.md docs/接口文档.md docs/数据库设计文档.md \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/PrescriptionDispenseCommand.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/PrescriptionDispenseResult.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MqConstants.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MedConsultMqAutoConfiguration.java \
  backend/medical-record-service/src/main/resources/schema.sql \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/entity/Prescription.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/dto/PrescriptionDTO.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/controller/PrescriptionController.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionService.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionServiceImpl.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionTxService.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/consumer/DispenseResultConsumer.java \
  backend/medical-record-service/src/test/java/com/medconsult/medicalrecord/prescription/PrescriptionDispenseStateMachineTest.java \
  backend/drug-service/src/main/resources/schema.sql \
  backend/drug-service/src/main/java/com/medconsult/drug/entity/DispenseCommandRecord.java \
  backend/drug-service/src/main/java/com/medconsult/drug/mapper/DispenseCommandRecordMapper.java \
  backend/drug-service/src/main/java/com/medconsult/drug/service/DispenseCommandService.java \
  backend/drug-service/src/main/java/com/medconsult/drug/consumer/DispenseCommandConsumer.java \
  backend/drug-service/src/main/java/com/medconsult/drug/service/DrugStockTxService.java \
  backend/drug-service/src/test/java/com/medconsult/drug/DispenseCommandConsumerTest.java \
  frontend/src/api/prescription.js frontend/src/mock/prescription.js \
  frontend/src/views/doctor/record/RecordWrite.vue \
  frontend/src/views/patient/prescription/PrescriptionList.vue \
  frontend/src/router/modules/patient.js \
  frontend/src/views/pharmacy/prescription/PrescriptionReview.vue \
  frontend/e2e/prescription-full-flow.spec.ts \
  frontend/e2e/prescription-dispense.spec.ts
git diff --cached --name-only
git commit -m "feat(prescription): 使用异步状态机保证调剂一致性"
```

### 任务 14：用执行计划决定审计与 AI 日志索引

**文件：**
- 创建：`scripts/db/benchmark/audit-log-explain.sql`
- 创建：`scripts/db/benchmark/ai-call-log-explain.sql`
- 修改：`backend/notification-service/src/main/resources/schema.sql`
- 修改：`backend/ai-service/src/main/resources/db/schema-ai.sql`

- [ ] **步骤 1：生成 10 万与 100 万行测试数据**

只在独立 benchmark schema 执行，不使用开发业务库。

- [ ] **步骤 2：执行高频查询的 `EXPLAIN ANALYZE`**

至少覆盖 operator + created_at、resource_type + created_at、AI patient_id + call_type + created_at。

- [ ] **步骤 3：只添加被计划证明有效的索引**

候选为 `(operator_id, created_at)`、`(resource_type, created_at)`、`(patient_id, call_type, created_at)`；若优化器不使用或写放大超过收益，不添加。

- [ ] **步骤 4：记录前后扫描行数和耗时**

退出条件：高频查询无 filesort 或扫描行数显著下降；插入吞吐下降在可接受阈值内。

- [ ] **步骤 5：Commit**

```bash
git status --short
git add scripts/db/benchmark/audit-log-explain.sql scripts/db/benchmark/ai-call-log-explain.sql \
  backend/notification-service/src/main/resources/schema.sql \
  backend/ai-service/src/main/resources/db/schema-ai.sql
git diff --cached --name-only
git commit -m "perf(database): 按执行计划优化审计与 AI 日志索引"
```

---

## 六、第 7–8 周：前端响应式、无障碍和 UI 一致性

### 任务 15：修复 HTML 基础与响应式设计令牌

**文件：**
- 修改：`frontend/index.html`
- 修改：`frontend/src/styles/variables.css`
- 修改：`frontend/src/styles/global.css`
- 创建：`frontend/src/composables/useResponsive.js`
- 修改：`frontend/src/views/common/Login.vue`
- 修改：`frontend/src/views/common/Register.vue`
- 创建：`frontend/e2e/mobile-responsive.spec.ts`

- [ ] **步骤 1：先写移动端失败测试**

```ts
for (const width of [375, 768, 1024, 1440]) {
  test(`登录页 ${width}px 无横向溢出`, async ({ page }) => {
    await page.setViewportSize({ width, height: 812 })
    await page.goto('/login')
    const metrics = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }))
    expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.clientWidth)
  })
}
```

再断言 `html[lang=zh-CN]` 和标题为“智慧医疗问诊辅助系统”。

- [ ] **步骤 2：运行测试确认 375px 失败**

```bash
npm --prefix frontend run test:e2e -- e2e/mobile-responsive.spec.ts
```

预期：375px 因 780px 文档宽度失败。

- [ ] **步骤 3：增加断点与可访问性令牌**

CSS 使用 640/768/1024/1280 px 媒体查询；增加 `--focus-ring`、`--content-max-width`、移动间距和触摸目标尺寸。CSS 自定义属性不能直接用于 `@media`，断点值在 CSS 和 composable 中用同一注释与测试固定。

- [ ] **步骤 4：实现 `useResponsive`**

使用 `window.matchMedia('(max-width: 767px)')`，在 mounted 时注册 `change`，unmounted 时移除；SSR/测试环境先判断 `window`。

- [ ] **步骤 5：登录/注册移动布局**

小于 768px 时隐藏非必要品牌特性区或移动到表单顶部，右侧宽度改为 `min(100%, 420px)`，页面不横向滚动，输入和按钮触摸高度至少 44px。

- [ ] **步骤 6：运行多视口测试并 Commit**

```bash
npm --prefix frontend run build
npm --prefix frontend run test:e2e -- e2e/mobile-responsive.spec.ts
git status --short
git add frontend/index.html frontend/src/styles/variables.css frontend/src/styles/global.css \
  frontend/src/composables/useResponsive.js frontend/src/views/common/Login.vue \
  frontend/src/views/common/Register.vue frontend/e2e/mobile-responsive.spec.ts
git diff --cached --name-only
git commit -m "fix(frontend): 建立响应式基础并修复移动端登录"
```

### 任务 16：改造主布局的移动导航与键盘语义

**文件：**
- 修改：`frontend/src/layouts/MainLayout.vue`
- 创建：`frontend/e2e/accessibility.spec.ts`
- 修改：`frontend/playwright.config.ts`
- 修改：`frontend/package.json`、`package-lock.json`

- [ ] **步骤 1：安装并固定 axe**

```bash
npm --prefix frontend install --save-dev @axe-core/playwright
```

- [ ] **步骤 2：写失败测试**

覆盖折叠按钮、通知铃、通知项可通过 Tab 聚焦与 Enter 激活；患者登录后直接访问 `/admin/user` 被拒；关键页面 axe 无 serious/critical violation。

- [ ] **步骤 3：改非语义点击元素**

折叠、通知铃和通知操作改为 `<button type="button">` 或 Element Plus Button；增加 `aria-label`、焦点样式和 unread count 的可访问文本。

- [ ] **步骤 4：移动端侧栏改 Drawer**

小于 768px 不保留固定 220px aside；菜单从左侧 Drawer 打开，路由跳转后自动关闭。桌面端保留 collapse 行为。

- [ ] **步骤 5：配置移动项目**

在 Playwright projects 增加 Pixel 7，但把纯桌面表格用例标记为 desktop-only；响应式/无障碍用例在 desktop + mobile 都跑。

- [ ] **步骤 6：验证并 Commit**

```bash
npm --prefix frontend run test:e2e -- e2e/accessibility.spec.ts e2e/login.spec.ts
npm --prefix frontend run build
git status --short
git add frontend/src/layouts/MainLayout.vue frontend/e2e/accessibility.spec.ts \
  frontend/playwright.config.ts frontend/package.json frontend/package-lock.json
git diff --cached --name-only
git commit -m "feat(frontend): 补齐移动导航与键盘可访问性"
```

### 任务 17：统一页面状态、分页和移动表格

**文件：**
- 创建：`frontend/src/components/common/PageState.vue`
- 创建：`frontend/src/components/common/ResponsiveTable.vue`
- 修改：`frontend/src/views/admin/system/UserManage.vue`
- 修改：`frontend/src/views/admin/audit/AuditLog.vue`
- 修改：`frontend/src/views/pharmacy/prescription/PrescriptionReview.vue`
- 修改：`frontend/src/views/patient/appointment/MyAppointment.vue`
- 创建：`frontend/e2e/error-recovery.spec.ts`

- [ ] **步骤 1：写接口失败测试**

用 `page.route` 让列表接口首次返回 500、第二次成功。断言页面显示“加载失败”和“重试”，点击重试后渲染数据；不得把错误显示成空列表。

- [ ] **步骤 2：实现 PageState**

props 为 `loading`、`error`、`empty`，emit `retry`；优先级为 loading → error → empty → slot。错误区使用 `role="alert"`。

- [ ] **步骤 3：实现 ResponsiveTable**

桌面渲染 table slot，移动端渲染 card slot；组件只负责布局，不复制领域字段映射。

- [ ] **步骤 4：首批迁移 4 个高价值列表**

接入真实 `page/pageSize/total`，切页时取消或忽略旧请求；错误后保留最后一次成功数据，并显示非阻塞错误提示。

- [ ] **步骤 5：运行测试并 Commit**

```bash
npm --prefix frontend run test:e2e -- e2e/error-recovery.spec.ts e2e/mobile-responsive.spec.ts
npm --prefix frontend run build
git status --short
git add frontend/src/components/common/PageState.vue frontend/src/components/common/ResponsiveTable.vue \
  frontend/src/views/admin/system/UserManage.vue frontend/src/views/admin/audit/AuditLog.vue \
  frontend/src/views/pharmacy/prescription/PrescriptionReview.vue \
  frontend/src/views/patient/appointment/MyAppointment.vue frontend/e2e/error-recovery.spec.ts
git diff --cached --name-only
git commit -m "feat(frontend): 统一列表错误恢复与响应式呈现"
```

### 任务 18：确认视觉方向后统一四角色工作台

**文件：**
- 修改：`frontend/src/styles/variables.css`、`global.css`
- 修改：`frontend/src/views/patient/home/PatientHome.vue`
- 修改：`frontend/src/views/doctor/workbench/DoctorWorkbench.vue`
- 修改：`frontend/src/views/pharmacy/workbench/PharmacyWorkbench.vue`
- 修改：`frontend/src/views/admin/system/UserManage.vue`（当前 `/admin` 的默认落地页）
- 创建：`frontend/e2e/visual-workbench.spec.ts`

- [ ] **步骤 1：准备 3 个代表性视觉方案**

使用患者、医生、药房和管理员各 1 个工作台内容制作并排原型：

1. 全角色延续玻璃拟态；
2. 患者柔和玻璃、工作人员高密度实心卡；
3. 全角色统一轻量医疗卡片，只保留局部玻璃层。

这是长期计划中唯一需要用户主观确认的门禁。未确认前不改生产页面。

- [ ] **步骤 2：把确认结果编码为令牌和组件语义**

只保留颜色、间距、圆角、阴影、表面和密度令牌；禁止在 46 个页面复制新的局部魔法数。

- [ ] **步骤 3：先迁移 4 个工作台**

保持信息架构与业务操作不变，只统一视觉层次、状态卡、快捷入口和响应式行为。

- [ ] **步骤 4：建立截图基线**

在 1440×900 和 375×812 下为 4 个工作台生成快照；动画和动态时间先稳定化，快照差异需人工 review 后更新。

- [ ] **步骤 5：验证与 Commit**

```bash
npm --prefix frontend run test:e2e -- e2e/visual-workbench.spec.ts e2e/mobile-responsive.spec.ts
npm --prefix frontend run build
git status --short
git add frontend/src/styles/variables.css frontend/src/styles/global.css \
  frontend/src/views/patient/home/PatientHome.vue \
  frontend/src/views/doctor/workbench/DoctorWorkbench.vue \
  frontend/src/views/pharmacy/workbench/PharmacyWorkbench.vue \
  frontend/src/views/admin/system/UserManage.vue frontend/e2e/visual-workbench.spec.ts
git diff --cached --name-only
git commit -m "feat(frontend): 统一四角色工作台视觉系统"
```

---

## 七、第 9–10 周：可观测性、CI 与包体积

### 任务 19：建立健康、指标与慢调用观测

**文件：**
- 修改：`backend/gateway/pom.xml`、`backend/auth-service/pom.xml`、`backend/patient-service/pom.xml`、`backend/outpatient-service/pom.xml`、`backend/drug-service/pom.xml`、`backend/medical-record-service/pom.xml`、`backend/notification-service/pom.xml`、`backend/ai-service/pom.xml`
- 修改：`backend/gateway/src/main/resources/application.yml`、`backend/auth-service/src/main/resources/application.yml`、`backend/patient-service/src/main/resources/application.yml`、`backend/outpatient-service/src/main/resources/application.yml`、`backend/drug-service/src/main/resources/application.yml`、`backend/medical-record-service/src/main/resources/application.yml`、`backend/notification-service/src/main/resources/application.yml`、`backend/ai-service/src/main/resources/application.yml`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java`
- 修改：`backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java`
- 修改：`backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionServiceImpl.java`
- 修改：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MessageDispatcher.java`
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/service/SymptomChatService.java`
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/service/FileUploadService.java`
- 修改：`infra/docker-compose.yml`
- 创建：`infra/prometheus/prometheus.yml`
- 创建：`docs/可观测性运行手册.md`

- [ ] **步骤 1：为 8 个运行模块加入依赖**

加入 `spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`。不在 common 聚合父 POM 中继承到非运行模块。

- [ ] **步骤 2：只暴露必要端点**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when_authorized
  metrics:
    tags:
      application: ${spring.application.name}
```

业务 Gateway 不转发 actuator；Prometheus 通过内网服务端口抓取。

- [ ] **步骤 3：增加关键业务指标**

至少记录登录结果、预约锁失败、调剂状态/耗时、审计消息状态、AI 外部调用耗时、文件上传大小/失败。tag 不得包含 patientId、recordId 等高基数或敏感值。

- [ ] **步骤 4：启动并验证**

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8081/actuator/prometheus | rg "jvm_memory|http_server"
```

逐服务验证 8/8；Prometheus targets 全部 UP。

- [ ] **步骤 5：Commit**

```bash
git status --short
git add backend/{gateway,auth-service,patient-service,outpatient-service,drug-service,medical-record-service,notification-service,ai-service}/pom.xml \
  backend/{gateway,auth-service,patient-service,outpatient-service,drug-service,medical-record-service,notification-service,ai-service}/src/main/resources/application.yml \
  backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java \
  backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/service/AppointmentServiceImpl.java \
  backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/prescription/service/PrescriptionServiceImpl.java \
  backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/MessageDispatcher.java \
  backend/ai-service/src/main/java/com/medconsult/ai/service/SymptomChatService.java \
  backend/ai-service/src/main/java/com/medconsult/ai/service/FileUploadService.java \
  infra/docker-compose.yml infra/prometheus/prometheus.yml docs/可观测性运行手册.md
git diff --cached --name-only
git commit -m "feat(observability): 接入健康检查与 Prometheus 指标"
```

### 任务 20：建立 GitHub Actions 合并门禁

**文件：**
- 创建：`.github/workflows/ci.yml`
- 修改：`frontend/playwright.config.ts`
- 修改：`docs/架构设计文档.md`

- [ ] **步骤 0：确认 medical-record 外部门禁已解除**

检查当前治理分支是否已经通过用户另行批准并提交的方式获得 `MedicalRecordFlowTest` 修复：

```bash
./backend/mvnw -q -f backend/pom.xml -pl :medical-record-service -am \
  -Dtest=MedicalRecordFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

只有测试通过才创建并宣称全量 CI 门禁完成。若原工作区 WIP 尚未由用户另行处置，本任务保持 `BLOCKED-EXTERNAL`；不得复制、提交或重写该 WIP，也不得在 CI 中排除该测试来制造绿色结果。

- [ ] **步骤 1：创建后端 job 与外部依赖**

使用 Temurin 21、Maven cache。job 必须声明：

```yaml
services:
  redis:
    image: redis:7.2-alpine
    ports:
      - 16379:6379
    options: >-
      --health-cmd "redis-cli ping"
      --health-interval 5s
      --health-timeout 3s
      --health-retries 20
  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    ports:
      - 5672:5672
    env:
      RABBITMQ_DEFAULT_USER: medconsult
      RABBITMQ_DEFAULT_PASS: medconsult123
    options: >-
      --health-cmd "rabbitmq-diagnostics -q ping"
      --health-interval 10s
      --health-timeout 5s
      --health-retries 20
```

然后运行：

```bash
./backend/mvnw -B -f backend/pom.xml verify
```

MySQL 由任务 3 的 Testcontainers 使用 GitHub runner Docker；现有 common-redis、common-mq、auth、drug、medical-record、notification 测试继续使用 service container 的固定端口。

- [ ] **步骤 2：创建前端 job**

使用 Node 20，执行：

```bash
npm ci
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

修改 `frontend/playwright.config.ts`：CI 项目不设置 `channel`，使用 `npx playwright install --with-deps chromium` 安装的 bundled Chromium；仅本地项目使用 `channel: process.env.PW_BROWSER_CHANNEL || 'chrome'`。这样 CI 不会寻找 runner 上不存在的系统 Chrome。

- [ ] **步骤 3：创建 docs-contract job**

运行 Python unittest、契约矩阵检查和 15 项总览完整性脚本。

- [ ] **步骤 4：上传报告**

失败时上传 Surefire、Playwright HTML、trace 与截图；成功时不上传包含业务数据的本地日志。

- [ ] **步骤 5：验证 workflow 语法并 Commit**

```bash
python scripts/review/verify_contract_matrix.py
npm --prefix frontend run build
git status --short
git add .github/workflows/ci.yml frontend/playwright.config.ts docs/架构设计文档.md
git diff --cached --name-only
git commit -m "ci: 建立后端前端与契约合并门禁"
```

不 push；由用户决定何时发布到 GitHub 触发远端 CI。

### 任务 21：按 bundle 报告治理首屏包体积

**文件：**
- 修改：`frontend/package.json`、`package-lock.json`
- 修改：`frontend/vite.config.mjs`
- 修改：`frontend/src/main.js`，只注册实际使用的 26 个图标：`ArrowDown`、`ArrowLeft`、`Bell`、`Box`、`Calendar`、`CircleCheck`、`Clock`、`Close`、`Cpu`、`Document`、`Expand`、`FirstAidKit`、`Fold`、`InfoFilled`、`Loading`、`Lock`、`Monitor`、`Phone`、`PictureFilled`、`Plus`、`Search`、`Suitcase`、`UploadFilled`、`User`、`UserFilled`、`Warning`

- [ ] **步骤 1：生成 bundle 报告**

安装与当前 Vite 2 兼容的 `rollup-plugin-visualizer` 固定版本，构建生成本地 HTML 报告但不提交报告。

- [ ] **步骤 2：确认主因**

若 bundle 报告确认全量 Element Plus 图标占 vendor 主要比例，移除 `src/main.js:7-16` 的 `import * as` 和遍历注册。改为从 `@element-plus/icons-vue` 显式 import 上述 26 个图标，并在 `main.js` 逐个 `app.component(name, component)` 注册；这样既避免导入全部图标，也兼容 `MainLayout.vue` 根据路由字符串动态渲染 `<component :is="menu.icon">`。运行 `rg -n '<el-icon|icon:' frontend/src`，确保实际引用均在 26 个图标清单内。

- [ ] **步骤 3：拆分稳定 vendor**

在 `rollupOptions.output.manualChunks` 至少分离 `vue`、`element-plus`、`axios/dayjs`；拆包目标是降低首屏加载与缓存失效，不用把警告阈值调大来隐藏问题。

- [ ] **步骤 4：测量前后结果**

记录入口 JS、首次加载传输量和构建 chunk。目标：不再出现单个 1.2 MB vendor chunk；登录首屏加载 JS 明显下降，所有路由仍能动态加载。

- [ ] **步骤 5：回归与 Commit**

```bash
npm --prefix frontend run build
npm --prefix frontend run test:e2e
git status --short
git add frontend/package.json frontend/package-lock.json frontend/vite.config.mjs frontend/src/main.js
git diff --cached --name-only
git commit -m "perf(frontend): 缩减全量图标并优化 vendor 拆包"
```

---

## 八、第 11–12 周：压测、全量 Review 与文档收敛

### 任务 22：建立可复现的局部性能基线

**文件：**
- 创建：`scripts/perf/k6/auth-users.js`
- 创建：`scripts/perf/k6/appointment-create.js`
- 创建：`scripts/perf/k6/medication-analysis.js`
- 创建：`scripts/perf/k6/prescription-dispense.js`
- 创建：`scripts/perf/README.md`
- 创建：`docs/性能基线-2026-07.md`

- [ ] **步骤 1：固定环境与数据规模**

记录服务版本、JDK、CPU/内存、数据库数据量、并发数和外部 AI mock 策略。性能测试只在隔离数据集执行。

- [ ] **步骤 2：为 4 个热点编写 k6 场景**

每个脚本输出 P50/P95/P99、吞吐和错误率。预约与调剂使用 setup 创建测试数据，teardown 清理测试前缀数据。

- [ ] **步骤 3：执行旧/新对比**

用户列表验证 U=10,000 时分页；用药分析验证 D=10 时 1 次批量调用；调剂验证 N=10 的同步受理和最终完成；预约验证并发锁与重复预约。

- [ ] **步骤 4：写性能基线文档**

每项记录输入规模、复杂度、SQL/Feign 数、资源峰值和结果；不能只写“更快”。

- [ ] **步骤 5：Commit**

```bash
git status --short
git add scripts/perf/k6/auth-users.js scripts/perf/k6/appointment-create.js \
  scripts/perf/k6/medication-analysis.js scripts/perf/k6/prescription-dispense.js \
  scripts/perf/README.md docs/性能基线-2026-07.md
git diff --cached --name-only
git commit -m "test(perf): 固化四条关键链路性能基线"
```

### 任务 23：执行 2–4 轮全栈收敛 Review

**文件：**
- 修改：`docs/全栈契约矩阵.md`
- 修改：`docs/修改建议.md`
- 修改：`docs/遗留问题复盘与实施状态.md`
- 创建：`docs/fullstack-review-2026-07-final.md`

- [ ] **步骤 1：第 0 步逐条核对 15 项总览**

每条标记已实现、部分实现、未实现、不适用或证据不足，并附最新 commit 与测试证据。

- [ ] **步骤 2：并行三线只读扫描**

前端线检查 46 个页面功能、UI/UX、权限与状态；后端线检查安全、性能、事务与资源；契约线运行矩阵和 docs 交叉检查。

- [ ] **步骤 3：第 1 轮修复高风险**

仅修复新发现的高风险正确性/安全问题；独立测试与 commit。

- [ ] **步骤 4：第 2 轮检查复杂度与潜在问题**

重点检查 N+1、无界集合、重复请求、定时器/请求清理、锁租约、缓存失效、MQ 幂等和补偿。

- [ ] **步骤 5：必要时进行第 3–4 轮**

第 2 轮仍有新高/中风险时继续；连续一轮无新高/中风险且全量验证通过即停止。第 4 轮仍不收敛则冻结改动，输出遗留清单。

- [ ] **步骤 6：全量验证**

```bash
./backend/mvnw -B -f backend/pom.xml verify
npm --prefix frontend run build
npm --prefix frontend run test:e2e
python -m unittest scripts/review/test_verify_contract_matrix.py -v
python scripts/review/verify_contract_matrix.py
```

运行 4 个 k6 场景，检查 Prometheus targets 与关键指标。

- [ ] **步骤 6.1：真实链路 Playwright 门禁**

在 mock E2E 之外新增真实后端联调用例，至少覆盖：四角色登录/越权拒绝、预约创建→支付→患者签到→医生叫号/完成、医生开方→药师审方→患者缴费→药师发药、症状问答 citations 展示、影像上传失败/超时/重试、药品真实创建 DTO、通知枚举和字符串 ID。每条用例检查 console error、network 4xx/5xx、加载/错误/重试状态。

- [ ] **步骤 6.2：移动端和无障碍门禁**

Playwright 项目必须覆盖 390×844、768×1024、1024×768、1440×900；关键页面不得水平溢出。键盘可达性覆盖登录、主导航、通知、表格/卡片操作、影像上传和弹窗关闭；axe 检查不允许新增 serious/critical 问题。

- [ ] **步骤 7：更新最终报告和 docs 状态**

报告包含前端功能覆盖、契约一致率、后端复杂度变化、Playwright 页面/视口覆盖、性能结果、待用户决策和遗留风险。

- [ ] **步骤 8：最终 docs Commit**

```bash
git status --short
git add docs/全栈契约矩阵.md docs/修改建议.md docs/遗留问题复盘与实施状态.md \
  docs/fullstack-review-2026-07-final.md
git diff --cached --name-only
git commit -m "docs(review): 完成长周期全栈治理收敛报告"
```

### 任务 24：分支收尾

**文件：**
- 不新增业务文件

- [ ] **步骤 1：确认工作区干净**

```bash
git status --short
git log --oneline --decorate -20
```

预期：无未提交文件；每个 commit 只做一件事。

- [ ] **步骤 2：运行完成审计**

逐项映射用户目标到 artifacts：docs 分析、前端 UI/UX、Playwright、后端性能/复杂度、及时 review、8–12 周计划、文档修订证据。

- [ ] **步骤 3：使用 finishing-a-development-branch**

向用户提供保留分支、合并、创建 PR 或清理 worktree 的选项。未经用户明确指示，不 push、不创建 PR、不合并。

---

## 九、跨任务验证矩阵

| 目标 | 最低验证 | 完成证据 |
| --- | --- | --- |
| docs 可信 | 15/15 状态 + 契约脚本 | `docs/修改建议.md`、`docs/全栈契约矩阵.md` |
| 门诊流程可回归 | MySQL/Redis Testcontainers | OutpatientFlowTest 6/6 执行业务断言 |
| RBAC 生效 | DB、缓存、fallback 开关测试 | token scope 不再无条件 `*` |
| internal 鉴权 | 用户触发 Feign + 服务触发 Feign | 双模策略与 `requireService()` 不冲突 |
| 行级过滤 | 正向/未认证/跨角色/跨 owner | SQL 条件与授权测试 |
| 患者签到 | 患者 SELF 签到、医生推进就诊 | 不再 403，患者不能越权推进 |
| 审计可靠 | 事务回滚、MQ 故障、重复投递 | 业务与 outbox 原子，audit_log 幂等 |
| 证件号安全 | 加密、盲索引、迁移、日志扫描 | 无新明文写入，可重入迁移 |
| 用户列表性能 | 10,000 用户、pageSize=20 | O(U) → O(pageSize)，SQL/Redis 数可复核 |
| 用药分析性能 | 10 个药品 | 10 次 Feign → 1 次 Feign |
| 处方完整用户流 | 医生开方→患者缴费→药师发药 | 前端真实链路 E2E 通过 |
| 调剂一致性 | 延迟、库存不足、重投、崩溃 | 无重复扣减，状态可恢复 |
| 移动端体验 | 375/768/1024/1440 | 无非预期横向溢出，导航可用 |
| 无障碍 | 键盘 + axe | 无 serious/critical violation |
| 错误恢复 | 首次 500、重试成功 | 页面不把错误伪装为空数据 |
| 包体积 | bundle 报告 + E2E | 消除 1.2 MB 单 vendor chunk |
| 可观测性 | 8 个 health + Prometheus | targets 8/8 UP，关键指标可查询 |
| CI | backend/frontend/e2e/docs jobs | GitHub PR 检查可执行 |
| 真实 E2E | console/network 断言 + real backend | mock-only 流程不作为上线门禁 |
| Review | 2–4 轮 | 连续一轮无新高/中风险或有明确遗留清单 |

## 十、禁止事项

- 不得把 `MedicalRecordFlowTest.java` 的用户 WIP 带入任何 commit。
- 不得用调整 Vite chunk warning 阈值代替包体积优化。
- 不得用 H2 放宽语法代替真实 MySQL 核心流程验证。
- 不得用 `AFTER_COMMIT` 再插 outbox 冒充原子可靠消息。
- 不得在证件号迁移完成前删除旧列或唯一约束回滚能力。
- 不得为了性能新增对外 API；批量接口只允许先补 internal 架构契约再实现。
- 不得只用单元测试证明性能改善；必须比较 I/O 次数和运行指标。
- 不得在用户确认视觉方案前批量修改四角色视觉风格。
- 不得跳过 `docs/修改建议.md` 第 0 步 checklist。
- 不得 push、开 PR 或合并，除非用户明确授权。
