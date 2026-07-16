# 安全修复设计：登录入口↔角色一致性校验 + 预约/病历接口补守卫

- 日期：2026-07-16
- 范围：安全漏洞修复（中等范围）
- 关联问题：用户反馈"在工作人员界面可以使用患者的账号登录"
- 关联文件（本轮会触及）：
  - `frontend/src/views/common/Login.vue`
  - `frontend/src/api/user.js`
  - `frontend/src/store/modules/user.js`
  - `backend/auth-service/.../dto/AuthDTO.java`
  - `backend/auth-service/.../service/AuthServiceImpl.java`
  - `backend/outpatient-service/.../appointment/controller/AppointmentController.java`
  - `backend/medical-record-service/.../medicalrecord/controller/MedicalRecordController.java`

## 1. 漏洞描述与根因

用户报告：在工作人员（医生/管理员/药房）登录入口，用患者账号 + 正确密码可以登录。

经探索，这是**两层缺陷叠加**，单独修任一层都无法真正闭合漏洞：

### 根因 A — 入口信息在登录链路中完全丢失

登录页 `Login.vue` 有两个入口（`selectedEntry` = `patient` / `staff`），但：

1. `loginForm` 只有 `{account, password, remember}`（`Login.vue:140-144`），**`selectedEntry` 从不进入请求体**，只用于切换皮肤色。
2. 前端 `loginApi` 只透传 `{account, password, remember}`（`api/user.js:21-30`）。
3. 后端 `AuthDTO.LoginRequest` 只有 `account` + `password`（`AuthDTO.java:96-104`），**没有入口/角色字段**。
4. 后端 `AuthServiceImpl.login()`（`AuthServiceImpl.java:181-243`）只校验 account+password+status，**没有任何"该账号是否匹配当前入口"的校验**。

后果：患者账号从工作人员入口输对密码 → 后端照常签发带 `primaryRole=PATIENT` 的合法 token。

### 根因 B — 关键工作人员接口零角色守卫

- `AppointmentController`（`AppointmentController.java`）和 `MedicalRecordController`（`MedicalRecordController.java`）**全部方法没有任何 `@Permission` 或 `@PreAuthorize`**。
- 即便患者被前端路由守卫挡在 `/doctor/*` 页面外，拿着合法 token 直接 `curl` 即可：
  - `PATCH /api/v1/appointments/{id}/status`（改就诊状态）
  - `POST /api/v1/medical-records`（写病历）/ `POST /api/v1/medical-records/{id}/archive`（归档）

### 关于 scope 通配 `*`（本轮不处理，说明为何不影响本方案）

登录签发 `scope = List.of("*")`（`AuthServiceImpl.java:222`），`JwtPayload.hasPermission` 见 `*` 放行（`JwtPayload.java:79`）。这使只声明 `code=` 的 `@Permission` 形同虚设。**但 `PermissionAspect` 的 `roles[]` 校验是独立分支**（`PermissionAspect.java:64-71`），与 scope 校验（74-79 行）互不短路。因此本轮给接口补 `roles={"DOCTOR"}` 等，在 scope=`*` 仍然有效——已读源码验证。scope 通配收口依赖 RBAC 五表落地，属独立工作项，不在本轮。

## 2. 修复范围（中等）

| 模块 | 改动 |
|---|---|
| 前端登录 | `loginForm` 增 `entry` 字段随 `POST /auth/login` 上送 |
| 后端 DTO | `LoginRequest` 增可选 `clientType` 字段（值 `PATIENT` / `STAFF`） |
| 后端登录服务 | `login()` 用 `sys_user` 的 `patient_id`/`doctor_id`/`pharmacist_id` 推断身份，校验入口↔身份一致，不一致抛 `FORBIDDEN` |
| 后端接口守卫 | `AppointmentController` 写/状态类方法补 `roles`；`MedicalRecordController` 写/归档类方法补 `roles` |

**不做**：scope 通配收口、RBAC 五表落地、前端 UI 调整、其它 4 个领域的改动（AI/UI/Playwright/时延）。

## 3. 详细设计

### 3.1 前端：携带入口信息（根因 A）

#### 3.1.1 `Login.vue` `loginForm`

```js
const loginForm = reactive({
  account: '',
  password: '',
  remember: false,
  entry: null      // 新增：'PATIENT' / 'STAFF'，由 selectedEntry 映射
})
```

`selectEntry(entry)` 接收小写 `'patient'/'staff'`（现有逻辑），在写入 `loginForm.entry` 时**映射为大写枚举**（与后端 `clientType` 契约一致）：
- `selectedEntry === 'patient'` → `loginForm.entry = 'PATIENT'`
- `selectedEntry === 'staff'` → `loginForm.entry = 'STAFF'`

（`backToEntrySelect` 重置时 `loginForm.entry = null`。）

实现提示：直接在 `selectEntry` 内做 `loginForm.entry = entry.toUpperCase()`，无需额外分支。

#### 3.1.2 `api/user.js` `loginApi`

无需改签名——`loginApi` 已透传整个 form 对象。确认请求体含 `entry` 字段即可。若 `loginApi` 显式列了字段，则补 `entry`。

#### 3.1.3 `store/modules/user.js` `login`

透传即可，无需改 action 逻辑（已传 form）。

### 3.2 后端：入口↔身份一致性校验（根因 A）

#### 3.2.1 `AuthDTO.LoginRequest` 增字段

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
     * 不传或为空时跳过入口校验（向后兼容老调用方/服务间调用）。
     */
    @Schema(description = "登录入口：PATIENT/STAFF（可选，不传则跳过入口校验）")
    private String clientType;
}
```

#### 3.2.2 `AuthServiceImpl.login()` 校验逻辑

在密码校验通过、签发 token 之前插入一致性校验。**身份推断不依赖 Redis**（Redis 抖动兜底 PATIENT 会误拒医生），改用 `sys_user` 的关联 ID 列：

```java
// 入口↔身份一致性校验（防止患者从工作人员入口登录）
// 身份判定基于 sys_user 的关联 ID 列（稳定），不依赖 Redis（避免抖动兜底误判）
if (req.getClientType() != null && !req.getClientType().isBlank()) {
    String ct = req.getClientType().trim().toUpperCase();
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
    // 其它 clientType 值视为非法，跳过校验（不阻断，但记日志）
}
```

身份推断规则说明：
- **isPatient** = `patient_id` 非空 且 `doctor_id`、`pharmacist_id` 均空（纯患者）。
- **isStaff** = `doctor_id` 或 `pharmacist_id` 任一非空（医生/药师/带档案关联的管理员）。
- **一人多角色**（如同时有 patient_id 和 doctor_id）= 既 isPatient 又 isStaff。当前规则下：从患者入口可登（isPatient=true），从工作人员入口也可登（isStaff=true）。这是**有意的宽松**——多角色用户本就可走任一入口。若将来要收紧（多角色只能走主角色入口），改用 primaryRole 判定，但 primaryRole 当前来自 Redis 不稳定，故本轮不这么做。
- **纯管理员**（HOSPITAL_ADMIN，三 ID 均空）= isPatient/isStaff 均为 false → 从任一入口都会被 `clientType` 校验拒绝。**这是已知限制**：管理员账号需补 `doctor_id`/`pharmacist_id` 关联，或后续给 `sys_user` 加 admin 标识。本轮先记录，需核对 seed 数据中管理员账号是否已有 pharmacist_id；若有则不受影响。

#### 3.2.3 兼容性

- `clientType` 可选：不传或为空 → 跳过校验。保证服务间调用、老客户端、自动化测试不受影响。
- 前端会始终上送 `clientType`，所以对真实用户场景始终生效。

### 3.3 后端：预约/病历接口补守卫（根因 B）

判定原则：**写操作 / 状态流转归医生（DOCTOR），查询类保留医患均可**（患者查自己的预约/病历是合法的）。患者合法操作不锁死。

#### 3.3.1 `AppointmentController`

| 方法 | HTTP | 业务动作 | 本轮补注解 |
|---|---|---|---|
| `create` | POST | 患者抢号挂号 | 保留（患者合法）+ `dataScope=SELF` |
| `detail` | GET | 查预约详情 | 保留 + `dataScope=SELF` |
| `list` | GET | 分页查预约 | 保留 + `dataScope=SELF` |
| `cancel` | POST | 取消预约 | 保留（患者可取消自己的）+ `dataScope=SELF` |
| `updatePayment` | PATCH | 更新支付状态 | `roles={"PATIENT"}`（患者支付动作）|
| `updateStatus` | PATCH | **更新就诊状态（接诊/完成，医生动作）** | **`roles={"DOCTOR"}`** |

> 注：`dataScope=SELF` 表示"数据归属校验交业务层"。本轮**不实现** SQL 改写或 owner 校验逻辑（那是 RBAC 五表的工作），只在注解上声明意图，并在 Service 层确认已有 owner 校验（若没有，记为遗留项）。核心防护靠 `roles`。

#### 3.3.2 `MedicalRecordController`

| 方法 | HTTP | 业务动作 | 本轮补注解 |
|---|---|---|---|
| `create` | POST | **创建病历（医生写）** | **`roles={"DOCTOR"}`** |
| `updateDraft` | PUT | **更新草稿病历（医生写）** | **`roles={"DOCTOR"}`** |
| `archive` | POST | **归档病历（医生）** | **`roles={"DOCTOR"}`** |
| `detail` | GET | 查病历详情 | 保留 + `dataScope=SELF` |
| `list` | GET | 分页查病历 | 保留 + `dataScope=SELF` |

> 患者查自己的病历是合法需求（需求文档明确），故查询接口保留医患可用，不加 `roles` 锁死，靠 `dataScope=SELF`（业务层 owner 校验）防越权读。

### 3.4 验证方式

1. **编译**：`mvnw -pl auth-service,outpatient-service,medical-record-service -am compile` 通过。
2. **前端构建**：`npm run build` 通过。
3. **后端单测**：现有 `AuthFlowTest` / `OutpatientFlowTest` / `MedicalRecordFlowTest` 仍通过（注意：这些测试若直接调 service 而非走切面，`@Permission` 不影响它们；若走 MockMvc 切面生效，需确认测试用的 token 角色匹配）。
4. **手动核验**（本轮不强求跑通全栈，但记录预期）：
   - 患者账号从工作人员入口登录 → `403 该账号不是工作人员账号`。
   - 患者账号从患者入口登录 → 正常。
   - 患者 token 调 `PATCH /appointments/{id}/status` → `403 角色权限不足`。
   - 患者 token 调 `POST /medical-records` → `403 角色权限不足`。

## 4. 遗留项与后续工作（明确不做，避免范围蔓延）

1. **scope 通配 `*` 收口**：需 RBAC 五表数据齐备 + 全仓 `@Permission(code=...)` 回归。独立工作项。
2. **`dataScope=SELF` 的真实落地**：本轮只在注解声明，SQL 改写/owner 校验交 RBAC 阶段。若 Service 层现无 owner 校验，记入遗留。
3. **管理员入口归属**：HOSPITAL_ADMIN 当前三 ID 均空，从 STAFF 入口会被拒。需给管理员账号补关联 ID 或加 admin 标识。
4. **其余四个领域**（RAG 能用 / ai-service 时延与 tool_call / Playwright 测试体系 / UI 升级）不在本轮，待后续逐块推进。

## 5. 风险

| 风险 | 缓解 |
|---|---|
| 补 `roles` 误锁死合法医生/患者操作 | 严格区分写/查：写操作才锁 DOCTOR，查询保留；逐方法核对需求文档 |
| 身份推断依赖 sys_user 关联 ID，seed 数据缺失导致误判 | 先核对 seed 数据中医生/药师账号是否都有 doctor_id/pharmacist_id；管理员问题已在遗留项说明 |
| `clientType` 大小写/拼写不一致 | 后端统一 `trim().toUpperCase()`；前端固定传大写枚举值 |
| 现有单测走切面导致角色不匹配而失败 | 实现 phase 先跑全量单测确认，失败则按测试 token 角色调整（测试数据问题，非生产逻辑问题）|
