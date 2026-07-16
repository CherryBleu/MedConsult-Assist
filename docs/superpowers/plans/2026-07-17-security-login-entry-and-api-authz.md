# 安全修复实现计划：登录入口↔角色校验 + 预约/病历接口补守卫

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 闭合"工作人员界面可用患者账号登录"漏洞——前端登录携带入口信息、后端校验入口↔身份一致、两个零守卫 Controller 补 `@Permission(roles)`。

**架构：** 前端 `loginForm` 增 `entry` 字段随 `POST /auth/login` 上送 → 后端 `LoginRequest` 增可选 `clientType` → `AuthServiceImpl.login()` 用 `sys_user` 关联 ID 列推断身份并校验入口一致性 → `AppointmentController`/`MedicalRecordController` 写操作补 `roles={"DOCTOR"}`。

**技术栈：** Spring Boot 3 + MyBatis-Plus + JUnit 5（后端）/ Vue 3 + Element Plus + Vite（前端）

**关联规格：** `docs/superpowers/specs/2026-07-16-security-login-entry-and-api-authz-design.md`

**已验证前提（实现时无需重复验证）：**
- `PermissionAspect.java:64-71` 的 `roles[]` 校验独立于 scope 通配（74-79 行），补 `roles` 在 `scope=*` 下仍有效。
- `loginApi(data)`（`api/user.js:25-29`）和 `store.login(loginForm)`（`store/modules/user.js:17`）都透传整个对象，前端只需在 `loginForm` 加字段。
- `AuthFlowTest` 登录 body 不含 `clientType`，`clientType` 可选 → 现有测试不受影响。

---

## 文件结构

| 文件 | 职责 | 改动类型 |
|---|---|---|
| `backend/auth-service/.../dto/AuthDTO.java` | `LoginRequest` 增 `clientType` 字段 | 修改 |
| `backend/auth-service/.../service/AuthServiceImpl.java` | `login()` 加入口↔身份校验 | 修改 |
| `backend/auth-service/.../AuthFlowTest.java` | 新增入口校验分支测试 | 修改 |
| `backend/outpatient-service/.../AppointmentController.java` | `updateStatus` 补 `roles={"DOCTOR"}` | 修改 |
| `backend/medical-record-service/.../MedicalRecordController.java` | `create`/`updateDraft`/`archive` 补 `roles={"DOCTOR"}` | 修改 |
| `frontend/src/views/common/Login.vue` | `loginForm` 增 `entry`，`selectEntry` 映射大写 | 修改 |

---

## 任务 1：后端 DTO 增 `clientType` 字段

**文件：**
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/dto/AuthDTO.java:95-104`

- [ ] **步骤 1：修改 `LoginRequest`，在 password 字段后增加 clientType**

把 `AuthDTO.java` 的 `LoginRequest`（当前只有 account/password）改为：

```java
    @Data
    @Schema(description = "登录请求")
    public static class LoginRequest {
        @Schema(description = "账号")
        @NotBlank(message = "账号不能为空")
        private String account;
        @Schema(description = "密码")
        @NotBlank(message = "密码不能为空")
        private String password;

        /**
         * 登录入口类型，用于校验账号身份是否匹配入口（防止患者从工作人员入口登录等越权）。
         * 可选：PATIENT（患者入口）/ STAFF（工作人员入口）。
         * 不传或为空时跳过入口校验（向后兼容老调用方/服务间调用/现有单测）。
         */
        @Schema(description = "登录入口：PATIENT/STAFF（可选，不传则跳过入口校验）")
        private String clientType;
    }
```

- [ ] **步骤 2：编译验证**

运行：`cd backend && ./mvnw.cmd -pl auth-service -am compile -q`
预期：BUILD SUCCESS（新增可选字段不影响编译）

- [ ] **步骤 3：Commit**

```bash
git add backend/auth-service/src/main/java/com/medconsult/auth/user/dto/AuthDTO.java
git commit -m "feat(auth): LoginRequest 增 clientType 字段——登录入口↔角色校验(1/4)"
```

---

## 任务 2：后端 `login()` 加入口↔身份校验

**文件：**
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java:206-211`（密码校验通过后、签发 token 前）

- [ ] **步骤 1：在 `login()` 密码校验通过后插入入口校验**

定位 `AuthServiceImpl.java` 第 206 行 `}` （密码校验 if 块结束）与第 208 行 `// 登录成功：更新最后登录时间` 之间，插入：

```java

        // 入口↔身份一致性校验（防止患者从工作人员入口登录等越权，根因 A 修复）。
        // 身份判定基于 sys_user 的关联 ID 列（稳定），不依赖 Redis（避免抖动兜底 PATIENT 误判医生）。
        String clientType = req.getClientType();
        if (clientType != null && !clientType.isBlank()) {
            String ct = clientType.trim().toUpperCase();
            boolean isPatient = u.getPatientId() != null
                    && u.getDoctorId() == null && u.getPharmacistId() == null;
            boolean isStaff = u.getDoctorId() != null || u.getPharmacistId() != null;
            if ("PATIENT".equals(ct) && !isPatient) {
                writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", "ENTRY_MISMATCH");
                throw new BusinessException(ErrorCode.FORBIDDEN, "该账号不是患者账号，请从工作人员入口登录");
            }
            if ("STAFF".equals(ct) && !isStaff) {
                writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", "ENTRY_MISMATCH");
                throw new BusinessException(ErrorCode.FORBIDDEN, "该账号不是工作人员账号，请从患者入口登录");
            }
            // 其它 clientType 值（非 PATIENT/STAFF）跳过校验，不阻断登录
        }
```

- [ ] **步骤 2：编译验证**

运行：`cd backend && ./mvnw.cmd -pl auth-service -am compile -q`
预期：BUILD SUCCESS（`u.getPatientId()`/`getDoctorId`/`getPharmacistId` 是 SysUser 已有字段；`ErrorCode.FORBIDDEN`/`BusinessException`/`writeLoginLog` 已在文件 import）

- [ ] **步骤 3：Commit**

```bash
git add backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java
git commit -m "feat(auth): login() 加入口↔身份一致性校验——登录入口↔角色校验(2/4)"
```

---

## 任务 3：后端入口校验单测

**文件：**
- 修改：`backend/auth-service/src/test/java/com/medconsult/auth/AuthFlowTest.java`（追加新测试方法）

- [ ] **步骤 1：在 `AuthFlowTest` 类末尾 `}`（第 149 行）前追加两个测试方法**

```java

    @Test
    void login_patientAccountFromStaffEntry_rejected() throws Exception {
        // mock 建档：返回固定 patientId（患者账号 patient_id 非空，doctor/pharmacist 空）
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9002L)));

        // 注册一个纯患者账号
        String regBody = """
                {"account":"bob","password":"P@ssw0rd","phone":"13900000888","name":"鲍勃","role":"PATIENT","idCard":"110101199003081234"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(jsonPath("$.code").value(0));

        // 患者账号从工作人员入口（clientType=STAFF）登录 → 应被拒（403）
        String staffLogin = """
                {"account":"bob","password":"P@ssw0rd","clientType":"STAFF"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(staffLogin))
                .andExpect(jsonPath("$.code").value(403001));

        // 同一患者账号从患者入口（clientType=PATIENT）登录 → 应成功
        String patientLogin = """
                {"account":"bob","password":"P@ssw0rd","clientType":"PATIENT"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(patientLogin))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").exists());

        // 不传 clientType（向后兼容）→ 应成功
        String noCt = """
                {"account":"bob","password":"P@ssw0rd"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(noCt))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void login_clientType_caseInsensitive() throws Exception {
        when(patientClient.createForRegister(any(PatientRegisterRequest.class)))
                .thenReturn(Result.ok(EntityIdDTO.of(9003L)));
        String regBody = """
                {"account":"carol","password":"P@ssw0rd","phone":"13900000777","name":"卡罗尔","role":"PATIENT","idCard":"110101199003091234"}""";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(regBody))
                .andExpect(jsonPath("$.code").value(0));

        // 小写 staff 也应被识别并拒绝（后端 toUpperCase 容错）
        String lowerStaff = """
                {"account":"carol","password":"P@ssw0rd","clientType":"staff"}""";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(lowerStaff))
                .andExpect(jsonPath("$.code").value(403001));
    }
```

- [ ] **步骤 2：运行测试**

运行：`cd backend && ./mvnw.cmd -pl auth-service test -Dtest=AuthFlowTest -q`
预期：3 个测试全 PASS（原 `fullAuthFlow_*` + 新增 2 个）。
前提：本地 Redis 已起（测试配 `localhost:16379`，见 `AuthFlowTest.java:47`）。

- [ ] **步骤 3：Commit**

```bash
git add backend/auth-service/src/test/java/com/medconsult/auth/AuthFlowTest.java
git commit -m "test(auth): 入口↔身份校验单测(患者从STAFF入口拒/从PATIENT入口通/大小写容错)——登录入口↔角色校验(3/4)"
```

---

## 任务 4：两个 Controller 补 `@Permission(roles)`

**文件：**
- 修改：`backend/outpatient-service/.../AppointmentController.java:69-75`（`updateStatus` 方法）
- 修改：`backend/medical-record-service/.../MedicalRecordController.java:31-36`（`create`）、`:56-63`（`updateDraft`）、`:65-72`（`archive`）

- [ ] **步骤 1：AppointmentController.updateStatus 补 roles**

在 `AppointmentController.java` 顶部 import 区加：
```java
import com.medconsult.common.security.Permission;
import com.medconsult.common.security.DataScope;
```
把 `updateStatus` 方法（第 69-75 行）注解改为：
```java
    /** §2.5.6 更新就诊状态（状态机校验，医生接诊/完成就诊动作） */
    @PatchMapping("/{appointmentId}/status")
    @Operation(summary = "更新就诊状态")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<AppointmentDTO.StatusResponse> updateStatus(@Parameter(description = "预约编号", required = true) @PathVariable String appointmentId,
                                                               @Valid @RequestBody AppointmentDTO.StatusUpdateRequest req) {
        return Result.ok(appointmentService.updateStatus(appointmentId, req));
    }
```

- [ ] **步骤 2：MedicalRecordController 三个写方法补 roles**

在 `MedicalRecordController.java` 顶部 import 区加：
```java
import com.medconsult.common.security.Permission;
import com.medconsult.common.security.DataScope;
```
把 `create`（31-36）、`updateDraft`（56-63）、`archive`（65-72）的 `@Operation` 上方各加 `@Permission`：

```java
    /** §2.6.1 创建电子病历（初始 DRAFT，医生写） */
    @PostMapping
    @Operation(summary = "创建电子病历")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<MedicalRecordDTO.CreateResponse> create(...) { ... }
```
```java
    /** §2.6.4 更新草稿病历（仅 DRAFT 可改，医生写） */
    @PutMapping("/{recordId}")
    @Operation(summary = "更新草稿病历")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<MedicalRecordDTO.UpdateResponse> updateDraft(...) { ... }
```
```java
    /** §2.6.5 归档病历（DRAFT → ARCHIVED，不可逆，医生） */
    @PostMapping("/{recordId}/archive")
    @Operation(summary = "归档病历")
    @Permission(roles = {"DOCTOR"}, dataScope = DataScope.ALL)
    public Result<MedicalRecordDTO.ArchiveResponse> archive(...) { ... }
```

- [ ] **步骤 3：编译验证（含依赖模块）**

运行：`cd backend && ./mvnw.cmd -pl outpatient-service,medical-record-service -am compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 4：跑两个服务的现有单测（确认 roles 不锁死合法医生流程）**

运行：`cd backend && ./mvnw.cmd -pl outpatient-service,medical-record-service test -q`
预期：现有 `OutpatientFlowTest` / `MedicalRecordFlowTest` 全 PASS。
注意：若这些测试直接调 Service（不经 Controller 切面），`@Permission` 不影响它们 → 应继续 PASS。若走 MockMvc 经切面，需确认测试用的 token 角色是 DOCTOR（否则会因新 roles 失败，那是测试数据问题，按需调整测试 token 角色）。

- [ ] **步骤 5：Commit**

```bash
git add backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/controller/AppointmentController.java backend/medical-record-service/src/main/java/com/medconsult/medicalrecord/medicalrecord/controller/MedicalRecordController.java
git commit -m "fix(security): AppointmentController/MedicalRecordController 写操作补 @Permission(roles=DOCTOR)——越权鉴权(4/4)"
```

---

## 任务 5：前端 Login.vue 携带 entry

**文件：**
- 修改：`frontend/src/views/common/Login.vue:140-148`（`loginForm` 定义 + `selectEntry`）

- [ ] **步骤 1：loginForm 增 entry 字段**

把 `Login.vue:140-144` 的 `loginForm` 改为：
```js
const loginForm = reactive({
  account: '',
  password: '',
  remember: false,
  entry: null      // 登录入口：'PATIENT'/'STAFF'，由 selectedEntry 映射大写后上送后端
})
```

- [ ] **步骤 2：selectEntry 映射大写并写入 entry**

把 `Login.vue:146-148` 的 `selectEntry` 改为：
```js
const selectEntry = (entry) => {
  selectedEntry.value = entry
  loginForm.entry = entry.toUpperCase()   // patient→PATIENT, staff→STAFF（与后端 clientType 契约一致）
}
```

- [ ] **步骤 3：backToEntrySelect 重置 entry**

把 `Login.vue:150-154` 的 `backToEntrySelect` 改为：
```js
const backToEntrySelect = () => {
  selectedEntry.value = null
  loginForm.account = ''
  loginForm.password = ''
  loginForm.entry = null
}
```

- [ ] **步骤 4：前端构建验证**

运行：`cd frontend && npm run build`
预期：build 成功（`loginForm.entry` 会随 `userStore.login(loginForm)` → `loginApi(loginForm)` 透传进 `POST /auth/login` 请求体，无需改 api/store）

- [ ] **步骤 5：Commit**

```bash
git add frontend/src/views/common/Login.vue
git commit -m "feat(login): loginForm 携带 entry 字段上送后端——前端配合入口↔角色校验"
```

---

## 任务 6：全量验证

- [ ] **步骤 1：后端全量编译**

运行：`cd backend && ./mvnw.cmd -pl auth-service,outpatient-service,medical-record-service -am compile -q`
预期：BUILD SUCCESS

- [ ] **步骤 2：后端相关单测全跑**

运行：`cd backend && ./mvnw.cmd -pl auth-service,outpatient-service,medical-record-service test -q`
预期：全 PASS（含任务 3 新增的入口校验测试）

- [ ] **步骤 3：前端构建**

运行：`cd frontend && npm run build`
预期：build 成功

- [ ] **步骤 4：更新文档闭环**

在 `docs/修改建议.md` 总览表或 `docs/遗留问题复盘与实施状态.md` 标注本轮安全修复状态（按 AGENTS.md review 流程约定形成闭环）。scope 通配收口、管理员入口归属记为遗留。

- [ ] **步骤 5：提交文档**

```bash
git add docs/
git commit -m "docs(security): 同步入口↔角色校验+接口补守卫实施状态，scope收口记遗留"
```
