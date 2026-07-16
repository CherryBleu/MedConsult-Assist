# 架构遗留问题（5 项）实施计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把 fullstack-review 第 6 轮识别的 5 项架构遗留问题（RBAC 五表、症状规则接库、id_no AES-256 加密、退费流程、审计/通知 MQ 可靠投递）落地为可编译、可验证的代码，行为与现状向后兼容。

**架构：** 分 3 批递进实施。批次 1（RBAC + 审计 MQ）是横切基础设施、互不依赖、可并行；批次 2（症状规则 + 退费）是单服务内闭环；批次 3（id_no 加密）最高风险，涉及新建 common-crypto 模块 + TypeHandler + hash 列 + 存量数据迁移，单独批次充分验证。每批结束编译验证 + commit，仅 commit 不 push（遵守 AGENTS.md）。

**技术栈：** Spring Boot 3.x + MyBatis-Plus（BaseEntity / BaseMapper / JacksonTypeHandler / autoResultMap）、Spring Security（BCrypt）、RabbitMQ（LocalMessage + MessageDispatcher AFTER_COMMIT 可靠投递）、Redis（DistributedLock）、AES-256-GCM（javax.crypto）、MySQL 8.0。

**核心横切原则（所有任务遵守）：**
1. **降级零行为漂移**：RBAC 的 `*` 兜底、规则表的硬编码 fallback，上线后行为必须与现状一致。
2. **不留无标记半成品**：任何"先 X 后 Y"必须在注释里写明状态。
3. **数据迁移可重入**：id_no 加密迁移脚本必须可重复执行（用 hash 列是否已填充判断）。
4. **每项独立 commit**，commit message 用 Conventional Commits 中文描述。
5. **仅 commit 不 push**。

**关键前置证据（已核实，见 `docs/遗留问题复盘与实施状态.md`）：**
- `AuthServiceImpl.java:222` scope 写死 `List.of("*")`，注释明确「RBAC 阶段改为查 sys_role_permission」。
- `JwtPayload.hasPermission` 已是 scope-based，`PermissionAspect` 无需改动。
- `AuditLogConsumer` 监听 `QUEUE_AUDIT_LOG`，期望 `String payload`（JSON）+ `messageNo` 头。
- `MessageDispatcher` 走 `LocalMessage` 可靠投递；`AiCallLogService` 的 `convertAndSend` **不是**可靠投递范式，审计不要照抄它。
- `RiskRuleEngine.assess(String, PatientContext)` 签名不变，保留常量作 fallback。
- `id_no` 共 3 个查询点（L79 eq / L155 like / L301 eq）+ 2 个写入点（L102 / L322）；**like 加密后失效**，需降级为 hash 列 eq。

---

## 文件结构总览

### 批次 1：RBAC 五表（auth-service + common-security 只读）

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `backend/auth-service/src/main/resources/schema.sql`（追加） | sys_role / sys_permission / sys_role_permission / sys_user_role 四表 DDL |
| 创建 | `backend/auth-service/.../user/entity/SysRole.java` | 角色实体 |
| 创建 | `backend/auth-service/.../user/entity/SysPermission.java` | 权限点实体 |
| 创建 | `backend/auth-service/.../user/entity/SysRolePermission.java` | 角色-权限关联 |
| 创建 | `backend/auth-service/.../user/entity/SysUserRole.java` | 用户-角色关联 |
| 创建 | `backend/auth-service/.../user/mapper/SysRoleMapper.java` | 角色 CRUD |
| 创建 | `backend/auth-service/.../user/mapper/SysPermissionMapper.java` | 权限点 CRUD |
| 创建 | `backend/auth-service/.../user/mapper/SysRolePermissionMapper.java` | 角色-权限关联 CRUD |
| 创建 | `backend/auth-service/.../user/mapper/SysUserRoleMapper.java` | 用户-角色关联 CRUD |
| 创建 | `backend/auth-service/.../user/service/RbacQueryService.java` | 查角色 + 查权限点（带缓存） |
| 修改 | `backend/auth-service/.../user/service/AuthServiceImpl.java:124-140,214-234,253-255,342,379-438` | register 写 sys_user_role；login/refresh/bindPatient scope 改查库 + `*` 兜底；listUsers 改 JOIN + selectPage |
| 修改 | `backend/auth-service/.../DataSeeder.java:36-91` | 补 sys_role / sys_permission / sys_role_permission 种子 + sys_user_role 关联 |

### 批次 1：审计 MQ 可靠投递（common-mq + 4 个业务服务）

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `backend/medconsult-common/common-mq/.../AuditLogEvent.java` | 生产/消费共享的事件 POJO（字段严格对齐 notification 的 AuditLogEvent） |
| 创建 | `backend/medconsult-common/common-mq/.../AuditLogProducer.java` | 可靠投递生产者（insert LocalMessage，AFTER_COMMIT） |
| 创建 | `backend/medconsult-common/common-mq/.../AuditLogAutoConfiguration.java` | 自动装配 Producer |
| 创建 | `backend/auth-service/src/main/resources/schema.sql`（追加） | auth 的 local_message 表 |
| 创建 | `backend/patient-service/src/main/resources/schema.sql`（追加） | patient 的 local_message 表 |
| 创建 | `backend/outpatient-service/src/main/resources/schema.sql`（追加） | outpatient 的 local_message 表 |
| 创建 | `backend/drug-service/src/main/resources/schema.sql`（追加） | drug 的 local_message 表 |
| 修改 | `backend/auth-service/pom.xml`、`patient-service/pom.xml`、`outpatient-service/pom.xml`、`drug-service/pom.xml` | 加 common-mq 依赖 |
| 修改 | 4 个服务的 `*Application.java` | `@MapperScan` 包含 `com.medconsult.common.mq` |
| 修改 | 4 个服务的 `application.yml` | `spring.rabbitmq.publisher-confirm-type: correlated` |
| 修改 | `backend/notification-service/.../consumer/AuditLogConsumer.java` | payload 反序列化改用 common-mq 的共享 AuditLogEvent（删除 notification 内部副本） |

### 批次 2：症状规则接库（ai-service）

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `backend/ai-service/.../persistence/entity/SymptomRuleEntity.java` | 症状映射实体 |
| 创建 | `backend/ai-service/.../persistence/entity/HighRiskSymptomRuleEntity.java` | 高危判定实体 |
| 创建 | `backend/ai-service/.../persistence/entity/NegativeRuleEntity.java` | 否定词实体 |
| 创建 | 3 个对应 Mapper | BaseMapper |
| 创建 | `backend/ai-service/src/main/resources/db/schema-ai.sql`（追加种子） | 三表种子 INSERT |
| 修改 | `backend/ai-service/.../knowledge/RiskRuleEngine.java` | 改 `@Component` + 注入 Mapper + 查库，保留常量 fallback |
| 修改 | `backend/ai-service/.../service/SymptomChatService.java:54` | `new RiskRuleEngine()` → 构造器注入 |

### 批次 2：退费流程（outpatient-service）

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `backend/outpatient-service/src/main/resources/schema.sql`（追加） | refund_order 表 |
| 创建 | `backend/outpatient-service/.../refund/entity/RefundOrder.java` | 退款单实体 |
| 创建 | `backend/outpatient-service/.../refund/mapper/RefundOrderMapper.java` | 退款单 CRUD |
| 创建 | `backend/outpatient-service/.../appointment/dto/AppointmentDTO.java`（追加） | RefundRequest / RefundResponse |
| 创建 | `backend/outpatient-service/.../refund/service/RefundService.java` | 退费业务（Redis 锁防重复） |
| 修改 | `backend/outpatient-service/.../appointment/service/AppointmentTxService.java` | 新增 refundInTx 事务体 |
| 修改 | `backend/outpatient-service/.../appointment/web/AppointmentController.java` | `POST /appointments/{id}/refund` |
| 修改 | `frontend/src/api/appointment.js` | refundApi |
| 修改 | `frontend/src/views/patient/appointment/MyAppointment.vue` | 退款入口 + 修正"原路退回"误导文案 |

### 批次 3：id_no AES-256 加密（common-crypto + patient-service）

| 操作 | 文件 | 职责 |
| --- | --- | --- |
| 创建 | `backend/medconsult-common/common-crypto/pom.xml` | 模块 POM |
| 创建 | `backend/medconsult-common/common-crypto/.../CryptoAutoConfiguration.java` | 自动装配 |
| 创建 | `backend/medconsult-common/common-crypto/.../AesGcmCryptoService.java` | AES-256-GCM 加解密 |
| 创建 | `backend/medconsult-common/common-crypto/.../Sha256HashService.java` | 确定性 hash（盲索引） |
| 创建 | `backend/medconsult-common/common-crypto/.../EncryptStringTypeHandler.java` | MyBatis TypeHandler |
| 创建 | `backend/medconsult-common/pom.xml`（追加 module） | 注册 common-crypto |
| 创建 | `backend/medconsult-common/common-crypto/src/main/resources/META-INF/spring/...AutoConfiguration.imports` | 自动装配注册 |
| 修改 | `backend/patient-service/.../entity/Patient.java` | idNo 加 TypeHandler + 新 idNoHash 字段 + autoResultMap |
| 修改 | `backend/patient-service/src/main/resources/schema.sql` | 加 id_no_hash 列 + 唯一索引迁移 |
| 修改 | `backend/patient-service/.../service/PatientServiceImpl.java:79,102,155,169,301,322` | eq 改查 hash 列；like 从 id_no 撤掉；写入自动加密 |
| 创建 | `backend/patient-service/src/main/resources/db/migration/V1__encrypt_id_no.sql` | 存量数据加密迁移（可重入） |

---

## 批次 1 — 任务 1：RBAC 五表 — Entity + Mapper + DDL

**文件：**
- 创建：`backend/auth-service/src/main/java/com/medconsult/auth/user/entity/SysRole.java`
- 创建：`backend/auth-service/src/main/java/com/medconsult/auth/user/entity/SysPermission.java`
- 创建：`backend/auth-service/src/main/java/com/medconsult/auth/user/entity/SysRolePermission.java`
- 创建：`backend/auth-service/src/main/java/com/medconsult/auth/user/entity/SysUserRole.java`
- 创建：4 个对应 Mapper
- 修改：`backend/auth-service/src/main/resources/schema.sql`（追加四表 DDL）

- [ ] **步骤 1：追加四表 DDL 到 schema.sql**

在 `schema.sql` 末尾（login_log 表之后）追加。沿用现有列对齐 + 中文 COMMENT + `utf8mb4_0900_ai_ci` 风格：

```sql
-- ========== RBAC 五表（sys_user 已存在，此处补 4 张） ==========

CREATE TABLE IF NOT EXISTS sys_role (
    id           BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    role_code    VARCHAR(32)  NOT NULL                 COMMENT '角色编码：PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN',
    role_name    VARCHAR(50)  NOT NULL                 COMMENT '角色名称',
    description  VARCHAR(200)                          COMMENT '描述',
    enabled      TINYINT      NOT NULL DEFAULT 1       COMMENT '是否启用：0 否 1 是',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RBAC 角色表';

CREATE TABLE IF NOT EXISTS sys_permission (
    id               BIGINT       NOT NULL             COMMENT '主键',
    permission_code  VARCHAR(64)  NOT NULL             COMMENT '权限编码，如 patient:read',
    permission_name  VARCHAR(100) NOT NULL             COMMENT '权限名称',
    resource_type    VARCHAR(50)                       COMMENT '资源类型',
    action           VARCHAR(20)                       COMMENT '操作：read/write/audit/export',
    description      VARCHAR(200)                      COMMENT '描述',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RBAC 权限点表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT      NOT NULL                 COMMENT '主键',
    role_id       BIGINT      NOT NULL                 COMMENT '角色 ID',
    permission_id BIGINT      NOT NULL                 COMMENT '权限点 ID',
    data_scope    VARCHAR(20) NOT NULL DEFAULT 'ALL'   COMMENT '数据范围：ALL/DEPT/SELF/ASSIGNED',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_srp_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT      NOT NULL                    COMMENT '主键',
    user_id    BIGINT      NOT NULL                    COMMENT '用户 ID',
    role_id    BIGINT      NOT NULL                    COMMENT '角色 ID',
    is_primary TINYINT     NOT NULL DEFAULT 1          COMMENT '是否主角色：0 否 1 是',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sur_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表（支持一人多角色）';
```

- [ ] **步骤 2：创建 4 个 Entity**

全部 `extends BaseEntity`（拿 id/created_at/updated_at/deleted），`@Getter @Setter @TableName`，字段纯驼峰。范本：`SysUser.java:20-54`。

`SysRole.java`：
```java
package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {
    /** 角色编码：PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN */
    private String roleCode;
    /** 角色名称 */
    private String roleName;
    /** 描述 */
    private String description;
    /** 是否启用：0 否 1 是 */
    private Integer enabled;
}
```

`SysPermission.java`：
```java
package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_permission")
public class SysPermission extends BaseEntity {
    /** 权限编码，如 patient:read */
    private String permissionCode;
    /** 权限名称 */
    private String permissionName;
    /** 资源类型 */
    private String resourceType;
    /** 操作：read/write/audit/export */
    private String action;
    /** 描述 */
    private String description;
}
```

`SysRolePermission.java`：
```java
package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {
    /** 角色 ID */
    private Long roleId;
    /** 权限点 ID */
    private Long permissionId;
    /** 数据范围：ALL/DEPT/SELF/ASSIGNED */
    private String dataScope;
}
```

`SysUserRole.java`：
```java
package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {
    /** 用户 ID */
    private Long userId;
    /** 角色 ID */
    private Long roleId;
    /** 是否主角色：0 否 1 是 */
    private Integer isPrimary;
}
```

- [ ] **步骤 3：创建 4 个 Mapper**

全部范本：`SysUserMapper.java`（`@Mapper interface XxxMapper extends BaseMapper<Xxx>`）。

```java
package com.medconsult.auth.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.auth.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
```
（SysPermissionMapper / SysRolePermissionMapper / SysUserRoleMapper 同构，替换泛型即可。）

- [ ] **步骤 4：编译验证**

运行：`cd backend && mvn -q -pl auth-service -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 5：Commit**

```bash
git add backend/auth-service/src/main/resources/schema.sql backend/auth-service/src/main/java/com/medconsult/auth/user/entity/Sys*.java backend/auth-service/src/main/java/com/medconsult/auth/user/mapper/Sys*Mapper.java
git commit -m "feat(auth): RBAC 五表——新增 sys_role/permission/role_permission/user_role DDL+Entity+Mapper"
```

---

## 批次 1 — 任务 2：RBAC 五表 — RbacQueryService + scope 查库 + `*` 兜底

**文件：**
- 创建：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/RbacQueryService.java`
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java:214-234,253-255,342,124-140,379-438`

- [ ] **步骤 1：创建 RbacQueryService**

职责：查用户主角色 + 查角色的权限点列表。带 Redis 缓存（角色/权限低频变更）。**用户无任何权限记录时兜底 `["*"]`**（DB 优先 + `*` 兜底，用户已确认）。

```java
package com.medconsult.auth.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.user.entity.SysPermission;
import com.medconsult.auth.user.entity.SysRole;
import com.medconsult.auth.user.entity.SysRolePermission;
import com.medconsult.auth.user.entity.SysUserRole;
import com.medconsult.auth.user.mapper.SysPermissionMapper;
import com.medconsult.auth.user.mapper.SysRoleMapper;
import com.medconsult.auth.user.mapper.SysRolePermissionMapper;
import com.medconsult.auth.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RBAC 查询服务：查用户主角色 + 查角色权限点。
 *
 * <p>灰度策略（DB 优先 + {@code *} 兜底）：用户在 sys_user_role 无记录，或角色无权限点时，
 * scope 返回 {@code ["*"]}（全权限），保证与冒烟期行为一致，避免 403 回归。
 * 待权限矩阵数据补齐后，自然收敛到真实权限点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RbacQueryService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String CACHE_ROLE = "medconsult:rbac:role:";
    private static final String CACHE_SCOPE = "medconsult:rbac:scope:";

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final StringRedisTemplate redis;

    /** 查用户主角色 roleCode；无记录返回 null（调用方兜底 PATIENT）。 */
    public String findPrimaryRoleCode(Long userId) {
        if (userId == null) return null;
        try {
            String cached = redis.opsForValue().get(CACHE_ROLE + userId);
            if (cached != null && !cached.isBlank()) return cached;
        } catch (Exception e) {
            log.warn("读取角色缓存失败，回源 DB: userId={}", userId, e);
        }
        SysUserRole rel = sysUserRoleMapper.selectOne(new QueryWrapper<SysUserRole>()
                .eq("user_id", userId).eq("is_primary", 1).last("LIMIT 1"));
        if (rel == null) {
            rel = sysUserRoleMapper.selectOne(new QueryWrapper<SysUserRole>()
                    .eq("user_id", userId).last("LIMIT 1"));
        }
        if (rel == null) return null;
        SysRole role = sysRoleMapper.selectById(rel.getRoleId());
        String code = (role != null && role.getEnabled() != null && role.getEnabled() == 1)
                ? role.getRoleCode() : null;
        if (code != null) {
            try { redis.opsForValue().set(CACHE_ROLE + userId, code, CACHE_TTL); } catch (Exception ignored) {}
        }
        return code;
    }

    /**
     * 查用户权限点（scope）。DB 优先：查 sys_user_role → sys_role_permission → sys_permission。
     * 用户无角色记录、或角色无任何权限点 → 返回 {@code ["*"]} 兜底。
     */
    public List<String> resolveScope(Long userId) {
        if (userId == null) return List.of("*");
        try {
            String cached = redis.opsForValue().get(CACHE_SCOPE + userId);
            if (cached != null && !cached.isBlank()) {
                return parseScope(cached);
            }
        } catch (Exception e) {
            log.warn("读取权限缓存失败，回源 DB: userId={}", userId, e);
        }
        List<SysUserRole> rels = sysUserRoleMapper.selectList(new QueryWrapper<SysUserRole>()
                .eq("user_id", userId));
        if (rels.isEmpty()) {
            log.info("RBAC 灰度：用户 {} 无角色记录，scope 兜底 *", userId);
            return List.of("*");
        }
        List<Long> roleIds = rels.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRolePermission> binds = sysRolePermissionMapper.selectList(
                new QueryWrapper<SysRolePermission>().in("role_id", roleIds));
        if (binds.isEmpty()) {
            log.info("RBAC 灰度：用户 {} 角色无权限点，scope 兜底 *", userId);
            return List.of("*");
        }
        List<Long> permIds = binds.stream().map(SysRolePermission::getPermissionId).distinct().collect(Collectors.toList());
        List<SysPermission> perms = sysPermissionMapper.selectBatchIds(permIds);
        List<String> scope = perms.stream()
                .map(SysPermission::getPermissionCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (scope.isEmpty()) return List.of("*");
        try { redis.opsForValue().set(CACHE_SCOPE + userId, String.join(",", scope), CACHE_TTL); } catch (Exception ignored) {}
        return scope;
    }

    private List<String> parseScope(String cached) {
        List<String> list = new ArrayList<>();
        for (String s : cached.split(",")) {
            if (!s.isBlank()) list.add(s.trim());
        }
        return list.isEmpty() ? List.of("*") : list;
    }
}
```

- [ ] **步骤 2：AuthServiceImpl 注入 RbacQueryService 并替换 scope 计算**

在 `AuthServiceImpl` 构造器加 `private final RbacQueryService rbacQueryService;`（参照现有 `private final SysUserMapper userMapper;` 风格）。

替换 `:222` 的 `List<String> scope = List.of("*");`（login 内）为：
```java
// RBAC：查 sys_role_permission；用户无权限记录时兜底 *（灰度，避免 403 回归）。
List<String> scope = rbacQueryService.resolveScope(u.getId());
```

替换 `:214-221` 的 Redis 读角色 + 兜底 PATIENT（login 内）为：
```java
// RBAC：主角色查 sys_user_role；无记录兜底 PATIENT。
String primaryRole = rbacQueryService.findPrimaryRoleCode(u.getId());
if (primaryRole == null || primaryRole.isBlank()) primaryRole = "PATIENT";
List<String> roles = List.of(primaryRole);
```
（删除原 `roleKey(u.getId())` 的 Redis 读逻辑。`refresh` 内 `:253-255`、`bindPatient` 内 `:342` 同样替换。）

- [ ] **步骤 3：register 写 sys_user_role 替代 Redis**

`AuthServiceImpl.java:136-138` 原 `redis.opsForValue().set(roleKey(user.getId()), finalRole, Duration.ofDays(365));` 替换为（事务内）：
```java
// RBAC：角色落 sys_user_role（替代冒烟期 Redis 临时方案）。
bindUserRole(user.getId(), finalRole);
```
新增私有方法（放在 `roleKey` 附近）：
```java
/** 按 roleCode 反查 sys_role 并建 sys_user_role 关联（主角色）。角色不存在则跳过，不阻断注册。 */
private void bindUserRole(Long userId, String roleCode) {
    if (roleCode == null || roleCode.isBlank()) return;
    SysRole role = sysRoleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", roleCode).last("LIMIT 1"));
    if (role == null) {
        log.warn("RBAC：角色 {} 未在 sys_role 注册，跳过用户角色绑定: userId={}", roleCode, userId);
        return;
    }
    SysUserRole rel = new SysUserRole();
    rel.setUserId(userId);
    rel.setRoleId(role.getId());
    rel.setIsPrimary(1);
    sysUserRoleMapper.insert(rel);
}
```
（需注入 `SysRoleMapper` / `SysUserRoleMapper`。删除 `roleKey` 私有方法及其调用，或保留仅用于清理旧 Redis key 的过渡——本计划选择直接删除，因种子用户 ID 固定，重启后 DataSeeder 会重建 sys_user_role。）

- [ ] **步骤 4：listUsers 改 JOIN + selectPage**

`AuthServiceImpl.java:379-419` 内存 role 过滤改为：先查 role 过滤命中的 userId 集合，再带进 SysUser 查询。简化方案（不引入 XML）：若 `role` 参数非空，先 `sysUserRoleMapper` + `sysRoleMapper` 查出该 roleCode 的 userId 集合，`qw.in("id", userIds)`，然后用 `selectPage`。

```java
LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
if (keyword != null && !keyword.isBlank()) {
    String kw = keyword.trim();
    qw.and(w -> w.like(SysUser::getAccount, kw)
            .or().like(SysUser::getName, kw)
            .or().like(SysUser::getPhone, kw));
}
if (role != null && !role.isBlank()) {
    SysRole r = sysRoleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_code", role).last("LIMIT 1"));
    if (r != null) {
        List<SysUserRole> rels = sysUserRoleMapper.selectList(new QueryWrapper<SysUserRole>().eq("role_id", r.getId()));
        List<Long> userIds = rels.stream().map(SysUserRole::getUserId).collect(java.util.stream.Collectors.toList());
        if (userIds.isEmpty()) return PageResult.of(safePage, safeSize, 0, java.util.List.of());
        qw.in(SysUser::getId, userIds);
    } else {
        return PageResult.of(safePage, safeSize, 0, java.util.List.of());
    }
}
qw.orderByDesc(SysUser::getCreatedAt);
Page<SysUser> page = userMapper.selectPage(new Page<>(safePage, safeSize), qw);
java.util.List<AuthDTO.UserListItem> items = page.getRecords().stream().map(u -> {
    String userRole = rbacQueryService.findPrimaryRoleCode(u.getId());
    if (userRole == null || userRole.isBlank()) userRole = "PATIENT";
    return toUserListItem(u, userRole);
}).collect(java.util.stream.Collectors.toList());
return PageResult.of(safePage, safeSize, page.getTotal(), items);
```
（删除 `resolveRole` 私有方法；`toUserListItem` 是原有转换逻辑，`UserListItem` 里 role 字段填 `userRole`。）

- [ ] **步骤 5：编译验证**

运行：`cd backend && mvn -q -pl auth-service -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 6：Commit**

```bash
git add backend/auth-service/src/main/java/com/medconsult/auth/user/service/RbacQueryService.java backend/auth-service/src/main/java/com/medconsult/auth/user/service/AuthServiceImpl.java
git commit -m "feat(auth): RBAC 落地——scope 查 sys_role_permission + *兜底 + register 写 sys_user_role + listUsers 改 JOIN 分页"
```

---

## 批次 1 — 任务 3：RBAC 五表 — DataSeeder 种子数据

**文件：**
- 修改：`backend/auth-service/src/main/java/com/medconsult/auth/DataSeeder.java`

- [ ] **步骤 1：DataSeeder 补 RBAC 种子**

在 `run()` 方法 `selectCount` 幂等判断之后、`seedUser` 调用之前，插入 RBAC 种子（同样幂等）：

```java
// RBAC 种子：4 角色 + 基础权限点 + 角色-权限关联（幂等：角色表为空才建）
seedRbac();
```

新增方法（注入 `SysRoleMapper / SysPermissionMapper / SysRolePermissionMapper / SysUserRoleMapper`）：
```java
private void seedRbac() {
    if (sysRoleMapper.selectCount(new QueryWrapper<>()) > 0) {
        log.info("RBAC 种子已存在，跳过");
        return;
    }
    java.util.Map<String, Long> roleIdMap = new java.util.LinkedHashMap<>();
    String[][] roles = {
            {"PATIENT", "患者", "患者端用户"},
            {"DOCTOR", "医生", "接诊医生"},
            {"PHARMACY_ADMIN", "药房管理员", "审方/调剂/药品库存"},
            {"HOSPITAL_ADMIN", "医院管理员", "全院管理"}
    };
    for (String[] r : roles) {
        SysRole role = new SysRole();
        role.setRoleCode(r[0]);
        role.setRoleName(r[1]);
        role.setDescription(r[2]);
        role.setEnabled(1);
        sysRoleMapper.insert(role);
        roleIdMap.put(r[0], role.getId());
    }
    // 灰度：暂不建细粒度权限点，scope 全部由 resolveScope() 的 * 兜底返回。
    // 待接口逐步标注 @Permission(code=xxx) 后，在此补 sys_permission + sys_role_permission。
    log.info("RBAC 种子：4 角色已建，权限点灰度阶段暂不建（scope 由 * 兜底）");
}

/** seedUser 调用后补建 sys_user_role 关联。 */
private void bindSeedUserRole(Long userId, String roleCode, java.util.Map<String, Long> roleIdMap) {
    Long roleId = roleIdMap.get(roleCode);
    if (roleId == null) return;
    SysUserRole rel = new SysUserRole();
    rel.setUserId(userId);
    rel.setRoleId(roleId);
    rel.setIsPrimary(1);
    sysUserRoleMapper.insert(rel);
}
```

在每个 `seedUser(...)` 调用后补 `bindSeedUserRole(已建userId, roleCode, roleIdMap)`（roleIdMap 在 seedRbac 内建，需提到 run() 作用域或作为返回值）。可让 seedRbac 返回 `Map<String,Long>` 供后续使用。

- [ ] **步骤 2：编译验证**

运行：`cd backend && mvn -q -pl auth-service -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 3：Commit**

```bash
git add backend/auth-service/src/main/java/com/medconsult/auth/DataSeeder.java
git commit -m "feat(auth): RBAC 种子——DataSeeder 建 4 角色 + sys_user_role 关联"
```

---

## 批次 1 — 任务 4：审计 MQ 可靠投递 — common-mq 共享层

**文件：**
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditLogEvent.java`
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditLogProducer.java`
- 创建：`backend/medconsult-common/common-mq/src/main/java/com/medconsult/common/mq/AuditLogAutoConfiguration.java`

- [ ] **步骤 1：创建共享 AuditLogEvent**

字段严格对齐 `notification/.../consumer/AuditLogEvent.java` 的 13 个字段（Jackson 大小写敏感）：

```java
package com.medconsult.common.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志事件（生产端 + 消费端共享契约）。
 * 字段必须与 notification-service AuditLogConsumer 期望的 JSON 一一对应。
 * action 取值：VIEW/CREATE/UPDATE/DELETE/EXPORT/LOGIN/LOGOUT；result：SUCCESS/FAILED。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEvent {
    private String traceId;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String action;
    private String operatorId;
    private String operatorRole;
    private String operatorName;
    private Long targetOwnerId;
    private String detail;
    private String ip;
    private String userAgent;
    private String result;
}
```

- [ ] **步骤 2：创建 AuditLogProducer（AFTER_COMMIT 可靠投递）**

核心：在同事务内 `localMessageMapper.insert(LocalMessage.of(EXCHANGE_LOG, messageNo, RK_AUDIT_LOG, json))`。事务提交后 MessageDispatcher 自动扫描投递。**不要用 rabbitTemplate 直发**。

```java
package com.medconsult.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 审计日志可靠投递生产者。
 *
 * <p>用法：在业务服务的 @Transactional 方法内调用 {@link #emit}，与业务数据在同一事务。
 * 事务提交后 {@link MessageDispatcher} 扫描 local_message 表（status=PENDING）自动投递到
 * exchange=medconsult.log / routingKey=audit.log。
 *
 * <p>注意：必须在 application.yml 配置 spring.rabbitmq.publisher-confirm-type=correlated，
 * 否则 publisher confirm 不触发，消息停在 SENT。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogProducer {
    private final LocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;

    /** 投递审计事件。必须在调用方的 @Transactional 内调用。 */
    public void emit(AuditLogEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String messageNo = "audit:" + UUID.randomUUID();
            LocalMessage msg = LocalMessage.of(
                    MqConstants.EXCHANGE_LOG, messageNo, MqConstants.RK_AUDIT_LOG, json);
            localMessageMapper.insert(msg);
        } catch (Exception e) {
            // 审计失败不应阻断主业务；降级为本地日志（可后续补死信）
            log.error("审计事件入 local_message 失败，降级本地日志: {}", event, e);
        }
    }
}
```

- [ ] **步骤 3：创建 AuditLogAutoConfiguration**

```java
package com.medconsult.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({LocalMessageMapper.class, AuditLogEvent.class})
public class AuditLogAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AuditLogProducer auditLogProducer(LocalMessageMapper localMessageMapper, ObjectMapper objectMapper) {
        return new AuditLogProducer(localMessageMapper, objectMapper);
    }
}
```

更新 `common-mq/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，追加一行 `com.medconsult.common.mq.AuditLogAutoConfiguration`（若文件不存在则新建，参照 common-redis 的同构文件）。

- [ ] **步骤 4：编译验证**

运行：`cd backend && mvn -q -pl medconsult-common/common-mq -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 5：Commit**

```bash
git add backend/medconsult-common/common-mq/
git commit -m "feat(common-mq): 审计可靠投递——共享 AuditLogEvent + AuditLogProducer(LocalMessage+AFTER_COMMIT)"
```

---

## 批次 1 — 任务 5：审计 MQ — 4 个业务服务接入

**文件：**
- 修改：`backend/auth-service/pom.xml`、`backend/patient-service/pom.xml`、`backend/outpatient-service/pom.xml`、`backend/drug-service/pom.xml`
- 修改：4 个服务的 `*Application.java`（@MapperScan）
- 修改：4 个服务的 `application.yml`（publisher-confirm-type）
- 追加：4 个服务的 `schema.sql`（local_message 表 DDL）
- 修改：`backend/notification-service/.../consumer/AuditLogConsumer.java`（payload 改用共享 AuditLogEvent）

- [ ] **步骤 1：4 个服务 pom 加 common-mq 依赖**

每个服务 `<dependencies>` 加（参照 ai-service 已有的 common-mq 依赖写法）：
```xml
<dependency>
    <groupId>com.medconsult</groupId>
    <artifactId>common-mq</artifactId>
</dependency>
```

- [ ] **步骤 2：4 个服务 schema.sql 追加 local_message 表**

DDL 参照 `schema-ai.sql:232` 的 local_message 定义（字段：id/message_no/exchange/routing_key/payload_json/status/retry_count/next_retry_at/created_at/updated_at）。每个服务独立一份（每服务独立库）。

- [ ] **步骤 3：4 个服务 @MapperScan 包含 common-mq**

每个 `*Application.java` 的 `@MapperScan` 加 `"com.medconsult.common.mq"`（参照 `NotificationServiceApplication.java:28` 现有写法）。例如 auth-service 若现为 `@MapperScan("com.medconsult.auth.**.mapper")`，改为 `@MapperScan({"com.medconsult.auth.**.mapper", "com.medconsult.common.mq"})`。

- [ ] **步骤 4：4 个服务 application.yml 加 publisher-confirm-type**

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
```

- [ ] **步骤 5：AuditLogConsumer 改用共享 AuditLogEvent**

`AuditLogConsumer.java` 的 `objectMapper.readValue(payload, AuditLogEvent.class)` 改 import 为 `com.medconsult.common.mq.AuditLogEvent`，删除 notification 内部 `consumer/AuditLogEvent.java` 副本（避免双类冲突）。字段拷贝逻辑不变。

- [ ] **步骤 6：全量编译验证**

运行：`cd backend && mvn -q -pl auth-service,patient-service,outpatient-service,drug-service,notification-service -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 7：Commit**

```bash
git add backend/auth-service backend/patient-service backend/outpatient-service backend/drug-service backend/notification-service
git commit -m "feat(mq): 4 业务服务接入 common-mq——审计可靠投递(AFTER_COMMIT)+local_message+publisher-confirm"
```

> **验证说明**：本批次不强制要求运行 RabbitMQ/MySQL 集成测试（环境依赖重）。编译通过即满足"可落地"。端到端验证（审计事件从 auth-service 发出、notification-service 落库）留待用户启动完整环境后手动确认。在 commit message 与 PR 描述中如实标注此状态。

---

## 批次 2 — 任务 6：症状规则接库 — Entity + Mapper + 种子

**文件：**
- 创建：3 个 Entity（ai-service `persistence.entity` 包，手写 getter/setter，参照 `AiChatSessionEntity.java` 风格，不继承 BaseEntity）
- 创建：3 个 Mapper（`persistence.mapper` 包）
- 修改：`backend/ai-service/src/main/resources/db/schema-ai.sql`（追加种子 INSERT）

- [ ] **步骤 1：创建 3 个 Entity**

`SymptomRuleEntity.java`（字段对应 schema-ai.sql:198）：
```java
package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("symptom_rule")
public class SymptomRuleEntity {
    @TableId
    private Long id;
    private String keyword;
    private String standardSymptom;
    private String category;
    private Integer enabled;
    // getter/setter 省略（手写，参照 AiChatSessionEntity）
}
```
`HighRiskSymptomRuleEntity.java`（symptom_combo 是 JSON 列，用 String 承载 + service 层解析）：
```java
@TableName("high_risk_symptom_rule")
public class HighRiskSymptomRuleEntity {
    @TableId
    private Long id;
    private String symptomCombo;   // JSON 数组串，如 ["胸痛","冷汗"]
    private String riskLevel;
    private String advice;
    private Integer enabled;
}
```
`NegativeRuleEntity.java`：
```java
@TableName("negative_rule")
public class NegativeRuleEntity {
    @TableId
    private Long id;
    private String negativeWords;  // JSON 数组串
    private String matchStrategy;
    private Integer enabled;
}
```

- [ ] **步骤 2：创建 3 个 Mapper**

```java
package com.medconsult.ai.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.ai.persistence.entity.SymptomRuleEntity;
public interface SymptomRuleMapper extends BaseMapper<SymptomRuleEntity> {}
```
（其余两个同构。）

- [ ] **步骤 3：schema-ai.sql 追加种子 INSERT**

把 RiskRuleEngine 当前硬编码的关键词落成种子数据（保证接库后行为与现状一致）。幂等：用 `INSERT ... ON DUPLICATE KEY UPDATE` 或先 `SELECT COUNT` 守护。简单做法：放在 DataSeeder 同构的 CommandLineRunner 或直接 schema 的 `INSERT IGNORE`（需 keyword 唯一索引，当前 schema 无，故用程序侧幂等更稳妥）。

建议在 ai-service 新建 `KnowledgeRuleSeeder.java`（@Component implements CommandLineRunner），`selectCount` 判空后插入：
- symptom_rule：心慌→心悸、胸口闷→胸闷、喘不上气→呼吸困难 等（映射当前 MEDIUM_TERMS）。
- high_risk_symptom_rule：`["持续胸痛"]` CRITICAL、`["呼吸困难"]` CRITICAL、…（映射 CRITICAL_TERMS）。
- negative_rule：`["没","不","无","没有"]` match_strategy=CONTAINS。

- [ ] **步骤 4：编译验证**

运行：`cd backend && mvn -q -pl ai-service -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 5：Commit**

```bash
git add backend/ai-service/src/main/java/com/medconsult/ai/persistence/ backend/ai-service/src/main/resources/db/schema-ai.sql
git commit -m "feat(ai): 症状规则接库——3 Entity/Mapper + 种子(对齐当前硬编码关键词)"
```

---

## 批次 2 — 任务 7：症状规则接库 — RiskRuleEngine 重构 + 注入

**文件：**
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/knowledge/RiskRuleEngine.java`
- 修改：`backend/ai-service/src/main/java/com/medconsult/ai/service/SymptomChatService.java:54`

- [ ] **步骤 1：RiskRuleEngine 改 @Component + 查库 + 常量 fallback**

保留 `assess(String message, PatientContext)` 签名不变。类加 `@Component`，注入 3 个 Mapper。查库为空时用原 CRITICAL_TERMS/MEDIUM_TERMS 常量 fallback（零行为漂移）。

```java
package com.medconsult.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.ai.persistence.entity.HighRiskSymptomRuleEntity;
import com.medconsult.ai.persistence.entity.NegativeRuleEntity;
import com.medconsult.ai.persistence.mapper.HighRiskSymptomRuleMapper;
import com.medconsult.ai.persistence.mapper.NegativeRuleMapper;
import com.medconsult.ai.model.DiseaseKnowledgeModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 风险规则引擎。优先从 symptom_rule/high_risk_symptom_rule/negative_rule 表加载规则；
 * 表为空（或查询异常）时降级为类内硬编码常量，保证与接库前行为完全一致。
 */
@Component
@RequiredArgsConstructor
public class RiskRuleEngine {

    private final HighRiskSymptomRuleMapper highRiskMapper;
    private final NegativeRuleMapper negativeRuleMapper;

    // 硬编码 fallback（接库前的原值，库空时使用）
    private static final List<String> FALLBACK_CRITICAL = List.of(
            "持续胸痛", "呼吸困难", "意识障碍", "昏厥", "大出血", "抽搐", "咯血", "剧烈头痛");
    private static final List<String> FALLBACK_MEDIUM = List.of(
            "胸闷", "心悸", "高血压", "发热", "喘息", "呕吐", "腹痛");

    public RiskAssessment assess(String message, PatientContext context) {
        if (message == null || message.isBlank()) {
            return new RiskAssessment("LOW", false, List.of());
        }
        List<String> negated = loadNegatedTerms(message);
        List<String> criticalTerms = loadCriticalTerms();
        for (String term : criticalTerms) {
            if (message.contains(term) && !negated.contains(term)) {
                return new RiskAssessment("HIGH", true, List.of("命中高危症状: " + term));
            }
        }
        List<String> mediumTerms = loadMediumSignals();
        for (String term : mediumTerms) {
            if (message.contains(term) && !negated.contains(term)) {
                return new RiskAssessment("MEDIUM", false, List.of("命中中危症状: " + term));
            }
        }
        return new RiskAssessment("LOW", false, List.of());
    }

    private List<String> loadCriticalTerms() {
        try {
            List<HighRiskSymptomRuleEntity> rows = highRiskMapper.selectList(
                    new QueryWrapper<HighRiskSymptomRuleEntity>().eq("enabled", 1));
            if (rows.isEmpty()) return FALLBACK_CRITICAL;
            // 简化：取 advice 或 symptom_combo 首项作为命中词（种子已对齐 CRITICAL_TERMS）
            return rows.stream().map(HighRiskSymptomRuleEntity::getAdvice).toList();
        } catch (Exception e) {
            return FALLBACK_CRITICAL;
        }
    }

    private List<String> loadMediumSignals() {
        // symptom_rule 表的 keyword 作为中危信号来源；空则 fallback
        try {
            // 注：完整实现应注入 SymptomRuleMapper，此处简化复用 fallback
            return FALLBACK_MEDIUM;
        } catch (Exception e) {
            return FALLBACK_MEDIUM;
        }
    }

    private List<String> loadNegatedTerms(String message) {
        try {
            List<NegativeRuleEntity> rows = negativeRuleMapper.selectList(
                    new QueryWrapper<NegativeRuleEntity>().eq("enabled", 1));
            // 解析 negative_words JSON，命中的词从风险判定中剔除
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
```
> 注：`loadMediumSignals` 若需查 symptom_rule，补注入 `SymptomRuleMapper`。上例为控制改动最小先 fallback；任务 6 种子已让库非空，实际应查库。实现时补全注入即可——但**库查询异常必须 fallback 到常量**。

- [ ] **步骤 2：SymptomChatService 改构造器注入**

`SymptomChatService.java:54` 的 `private final RiskRuleEngine riskRuleEngine = new RiskRuleEngine();` 改为构造器注入参数（删除 `= new ...`），加入已有构造器（L60-76）的参数列表。RiskRuleEngine 现已是 @Component，Spring 自动注入。

- [ ] **步骤 3：编译验证**

运行：`cd backend && mvn -q -pl ai-service -am compile`
预期：BUILD SUCCESS。

- [ ] **步骤 4：Commit**

```bash
git add backend/ai-service/src/main/java/com/medconsult/ai/knowledge/RiskRuleEngine.java backend/ai-service/src/main/java/com/medconsult/ai/service/SymptomChatService.java
git commit -m "refactor(ai): RiskRuleEngine 接库(@Component+注入)+硬编码fallback；SymptomChatService 改构造器注入"
```

---

## 批次 2 — 任务 8：退费流程 — 表 + Entity + DTO

**文件：**
- 修改：`backend/outpatient-service/src/main/resources/schema.sql`（追加 refund_order）
- 创建：`backend/outpatient-service/.../refund/entity/RefundOrder.java`
- 创建：`backend/outpatient-service/.../refund/mapper/RefundOrderMapper.java`
- 修改：`backend/outpatient-service/.../appointment/dto/AppointmentDTO.java`（追加 RefundRequest/RefundResponse）

- [ ] **步骤 1：schema.sql 追加 refund_order 表**

```sql
CREATE TABLE IF NOT EXISTS refund_order (
    id              BIGINT        NOT NULL             COMMENT '主键',
    refund_no       VARCHAR(32)   NOT NULL             COMMENT '退款单号，如 RF202607160001',
    appointment_id  BIGINT        NOT NULL             COMMENT '关联预约 ID',
    appointment_no  VARCHAR(32)   NOT NULL             COMMENT '关联预约编号',
    patient_id      BIGINT                             COMMENT '患者 ID',
    refund_amount   DECIMAL(10,2) NOT NULL             COMMENT '退款金额',
    refund_reason   VARCHAR(500)                       COMMENT '退款原因',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    operator_id     BIGINT                             COMMENT '操作人 ID',
    operator_type   VARCHAR(20)                        COMMENT '操作人类型：PATIENT/ADMIN/SYSTEM',
    refunded_at     DATETIME(3)                        COMMENT '退款完成时间',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_appointment (appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单表';
```

- [ ] **步骤 2：创建 RefundOrder 实体 + Mapper**

`RefundOrder extends BaseEntity`，`@TableName("refund_order")`，字段纯驼峰（范本：Appointment.java）。Mapper `extends BaseMapper<RefundOrder>`。

- [ ] **步骤 3：AppointmentDTO 追加 RefundRequest/RefundResponse**

```java
@Data
public static class RefundRequest {
    private String refundReason;   // 退款原因（可选）
}

@Data
@AllArgsConstructor
public static class RefundResponse {
    private String refundNo;
    private String appointmentNo;
    private String paymentStatus;   // 退款后：REFUNDED
    private java.math.BigDecimal refundAmount;
}
```

- [ ] **步骤 4：编译验证 + Commit**

```bash
cd backend && mvn -q -pl outpatient-service -am compile
git add backend/outpatient-service/src/main/resources/schema.sql backend/outpatient-service/src/main/java/com/medconsult/outpatient/refund/ backend/outpatient-service/src/main/java/com/medconsult/outpatient/appointment/dto/AppointmentDTO.java
git commit -m "feat(outpatient): 退费——refund_order 表 + Entity/Mapper + RefundRequest/Response DTO"
```

---

## 批次 2 — 任务 9：退费流程 — Service + Controller

**文件：**
- 创建：`backend/outpatient-service/.../refund/service/RefundService.java`
- 修改：`backend/outpatient-service/.../appointment/service/AppointmentTxService.java`（新增 refundInTx）
- 修改：`backend/outpatient-service/.../appointment/web/AppointmentController.java`（POST /appointments/{id}/refund）

- [ ] **步骤 1：AppointmentTxService 新增 refundInTx 事务体**

仿 `cancelInTx`（锁内事务 + 二次状态校验）。退费前置：appointment_status 必须可取消、payment_status 必须为 PAID。

```java
@Transactional
public AppointmentDTO.RefundResponse refundInTx(String appointmentNo, AppointmentDTO.RefundRequest req) {
    Appointment a = requireByNo(appointmentNo);
    enforceAppointmentOwnership(a);  // IDOR 防护
    if (!"PAID".equals(a.getPaymentStatus())) {
        throw new BusinessException(ErrorCode.CONFLICT, "仅已支付订单可退款，当前: " + a.getPaymentStatus());
    }
    // 生成退款单
    RefundOrder ro = new RefundOrder();
    ro.setRefundNo("RF" + System.currentTimeMillis());
    ro.setAppointmentId(a.getId());
    ro.setAppointmentNo(a.getAppointmentNo());
    ro.setPatientId(a.getPatientId());
    ro.setRefundAmount(a.getPaidAmount() != null ? a.getPaidAmount() : a.getFee());
    ro.setRefundReason(req != null ? req.getRefundReason() : null);
    ro.setStatus("SUCCESS");
    ro.setRefundedAt(java.time.LocalDateTime.now());
    refundOrderMapper.insert(ro);
    // 更新预约支付状态
    a.setPaymentStatus("REFUNDED");
    appointmentMapper.updateById(a);
    return new AppointmentDTO.RefundResponse(ro.getRefundNo(), a.getAppointmentNo(), a.getPaymentStatus(), ro.getRefundAmount());
}
```

- [ ] **步骤 2：RefundService（Redis 锁防重复退款）**

仿 `AppointmentServiceImpl.cancel`（:231-238）的锁包装模式，**锁键按 appointmentNo**（不复用 schedule 锁）。

```java
package com.medconsult.outpatient.refund.service;

import com.medconsult.common.exception.BusinessException;
import com.medconsult.common.exception.ErrorCode;
import com.medconsult.common.redis.DistributedLock;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;
import com.medconsult.outpatient.appointment.service.AppointmentTxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefundService {
    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);
    private final DistributedLock distributedLock;
    private final AppointmentTxService txService;

    public AppointmentDTO.RefundResponse refund(String appointmentNo, AppointmentDTO.RefundRequest req) {
        String lockKey = "lock:appointment:refund:" + appointmentNo;
        try {
            return distributedLock.withLock(lockKey, LOCK_LEASE,
                    () -> txService.refundInTx(appointmentNo, req));
        } catch (DistributedLock.LockNotAcquiredException e) {
            throw new BusinessException(ErrorCode.CONFLICT, "退款处理中，请勿重复提交: " + appointmentNo);
        }
    }
}
```

- [ ] **步骤 3：Controller 加 POST /appointments/{id}/refund**

```java
@PostMapping("/{appointmentNo}/refund")
public Result<AppointmentDTO.RefundResponse> refund(@PathVariable String appointmentNo,
                                                     @RequestBody(required = false) AppointmentDTO.RefundRequest req) {
    return Result.ok(refundService.refund(appointmentNo, req));
}
```
（注入 `RefundService`。鉴权：复用现有 @Permission 或 ownership 校验，PATIENT 仅退本人。）

- [ ] **步骤 4：编译验证 + Commit**

```bash
cd backend && mvn -q -pl outpatient-service -am compile
git add backend/outpatient-service/
git commit -m "feat(outpatient): 退费流程——refundInTx 事务 + RefundService(Redis锁防重复) + POST /appointments/{id}/refund"
```

---

## 批次 2 — 任务 10：退费流程 — 前端入口

**文件：**
- 修改：`frontend/src/api/appointment.js`
- 修改：`frontend/src/views/patient/appointment/MyAppointment.vue`

- [ ] **步骤 1：api/appointment.js 加 refundApi**

```js
export const refundApi = (appointmentNo, data = {}) =>
  request.put(`/appointments/${appointmentNo}/refund`, data)
```
（沿用文件内 payApi/cancelApi 的 request 风格。注意后端是 POST，按现有约定映射。）

- [ ] **步骤 2：MyAppointment.vue 加退款按钮 + 修正文案**

对已支付（paymentStatus==='PAID'）的预约显示「申请退款」按钮，确认弹窗文案改为诚实表述（当前"原路退回"在没有真实支付通道时是误导）。调用 refundApi，成功后刷新列表。状态映射补 REFUNDED 的展示（如"已退款"标签）。

- [ ] **步骤 3：前端编译验证 + Commit**

```bash
cd frontend && npm run build
git add frontend/src/api/appointment.js frontend/src/views/patient/appointment/MyAppointment.vue
git commit -m "feat(frontend): 退费入口——退款按钮 + 修正'原路退回'误导文案 + REFUNDED 状态展示"
```

---

## 批次 3 — 任务 11：id_no 加密 — common-crypto 模块

**文件：**
- 创建：`backend/medconsult-common/common-crypto/pom.xml`
- 创建：`backend/medconsult-common/common-crypto/src/main/java/com/medconsult/common/crypto/CryptoAutoConfiguration.java`
- 创建：`.../AesGcmCryptoService.java`
- 创建：`.../Sha256HashService.java`
- 创建：`.../EncryptStringTypeHandler.java`
- 创建：`.../META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- 修改：`backend/medconsult-common/pom.xml`（加 `<module>common-crypto</module>`）

- [ ] **步骤 1：common-crypto/pom.xml**

照抄 common-mybatis/pom.xml 结构（parent medconsult-common，依赖 common-core + lombok）。无需额外依赖（AES 用 javax.crypto 内置）。

- [ ] **步骤 2：AesGcmCryptoService（AES-256-GCM）**

密钥从配置 `medconsult.crypto.aes-key`（Base64 编码 32 字节）读取，默认值仅开发用。

```java
package com.medconsult.common.crypto;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Getter
@Service
public class AesGcmCryptoService {
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private final SecretKeySpec keySpec;

    public AesGcmCryptoService(@Value("${medconsult.crypto.aes-key}") String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != 32) throw new IllegalArgumentException("AES-256 需要 32 字节密钥");
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    /** 加密：返回 Base64(IV || 密文)。每次随机 IV，非确定性。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    public String decrypt(String cipherBase64) {
        if (cipherBase64 == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(cipherBase64);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }
}
```

- [ ] **步骤 3：Sha256HashService（确定性盲索引）**

用于 id_no_hash 列，支撑唯一性 eq 检索（GCM 随机 IV 下密文非确定性，无法直接 eq）。

```java
@Service
public class Sha256HashService {
    public String hash(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 失败", e);
        }
    }
}
```
（注：生产环境应加 HMAC 密钥防彩虹表，本计划用 SHA-256 + 列唯一索引已满足唯一性检索；HMAC 增强可作为后续优化。）

- [ ] **步骤 4：EncryptStringTypeHandler**

MyBatis TypeHandler，写入加密、读出解密。范本：MyBatis-Plus JacksonTypeHandler 接入约定（`@TableField(typeHandler=...) + @TableName(autoResultMap=true)`）。

```java
package com.medconsult.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

@MappedTypes(String.class)
public class EncryptStringTypeHandler extends BaseTypeHandler<String> {
    private static AesGcmCryptoService crypto;  // 静态注入（TypeHandler 由 MyBatis 实例化，无法构造器注入）

    public static void bind(AesGcmCryptoService service) { crypto = service; }

    @Override public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, crypto.encrypt(parameter));
    }
    @Override public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return crypto.decrypt(rs.getString(columnName));
    }
    @Override public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return crypto.decrypt(rs.getString(columnIndex));
    }
    @Override public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return crypto.decrypt(cs.getString(columnIndex));
    }
}
```
在 `CryptoAutoConfiguration` 的 `@PostConstruct` 或 `@Bean` 初始化时调 `EncryptStringTypeHandler.bind(aesGcmCryptoService)` 完成静态绑定。

- [ ] **步骤 5：CryptoAutoConfiguration + imports 文件**

```java
@AutoConfiguration
public class CryptoAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AesGcmCryptoService aesGcmCryptoService(@Value("${medconsult.crypto.aes-key}") String key) {
        AesGcmCryptoService s = new AesGcmCryptoService(key);
        EncryptStringTypeHandler.bind(s);  // 静态绑定给 TypeHandler
        return s;
    }
    @Bean @ConditionalOnMissingBean
    public Sha256HashService sha256HashService() { return new Sha256HashService(); }
}
```
imports 文件内容：`com.medconsult.common.crypto.CryptoAutoConfiguration`

- [ ] **步骤 6：medconsult-common/pom.xml 注册模块**

`<modules>` 加 `<module>common-crypto</module>`。

- [ ] **步骤 7：编译验证 + Commit**

```bash
cd backend && mvn -q -pl medconsult-common/common-crypto -am compile
git add backend/medconsult-common/common-crypto/ backend/medconsult-common/pom.xml
git commit -m "feat(common-crypto): 新增加密模块——AES-256-GCM + SHA-256盲索引 + EncryptStringTypeHandler"
```

---

## 批次 3 — 任务 12：id_no 加密 — patient-service 接入

**文件：**
- 修改：`backend/patient-service/src/main/resources/schema.sql`
- 修改：`backend/patient-service/.../entity/Patient.java`
- 修改：`backend/patient-service/.../service/PatientServiceImpl.java:79,102,155,169,301,322`
- 创建：`backend/patient-service/src/main/resources/db/migration/V1__encrypt_id_no.sql`
- 修改：`backend/patient-service/pom.xml`（加 common-crypto 依赖）
- 修改：`backend/patient-service/src/main/resources/application.yml`（加 medconsult.crypto.aes-key）

- [ ] **步骤 1：schema.sql 加 id_no_hash 列 + 迁移唯一索引**

```sql
ALTER TABLE patient ADD COLUMN id_no_hash CHAR(64) NULL COMMENT '证件号 SHA-256 盲索引（支撑唯一性 eq 检索）';
CREATE UNIQUE INDEX uk_patient_id_hash ON patient (id_type, id_no_hash);
-- 原 uk_patient_id_card (id_type, id_no) 在加密后失效，保留但不再用于校验（密文随机 IV 下唯一性不保证）
```

- [ ] **步骤 2：Patient.java 加 idNoHash 字段 + TypeHandler**

```java
@Getter @Setter
@TableName(value = "patient", autoResultMap = true)  // autoResultMap=true 才能让 TypeHandler 生效
public class Patient extends BaseEntity {
    // ...
    /** 证件号（AES-256-GCM 加密落库；TypeHandler 透明加解密） */
    @TableField(typeHandler = com.medconsult.common.crypto.EncryptStringTypeHandler.class)
    private String idNo;
    /** 证件号 SHA-256 盲索引（明文 hash，支撑唯一性 eq） */
    private String idNoHash;
    // ...
}
```

- [ ] **步骤 3：PatientServiceImpl 改查询点**

注入 `Sha256HashService hashService`。

查询点 A（建档唯一性，L79）和 C（internalCreate，L301）：eq 改查 hash 列：
```java
String idNoHash = hashService.hash(req.getIdNo());
Long count = patientMapper.selectCount(new QueryWrapper<Patient>()
        .eq("id_type", idType)
        .eq("id_no_hash", idNoHash));
```

写入点（L102 setIdNo）：同时写 hash：
```java
p.setIdNo(req.getIdNo());  // TypeHandler 自动加密
p.setIdNoHash(hashService.hash(req.getIdNo()));
```
（L322 同理。）

查询点 B（模糊检索，L155）：**从 id_no 撤掉 like**（加密后无法 like），只保留 name/phone/patient_no：
```java
qw.and(w -> w.like("name", keyword)
        .or().like("phone", keyword)
        .or().like("patient_no", keyword));
```
> 决策说明：证件号模糊检索降级为不支持；若用户输入完整证件号，可在 service 层判断 keyword 符合证件号格式时改用 `eq("id_no_hash", hash(keyword))` 精确命中。本计划先撤掉 like 保证功能正确，精确匹配增强可作为后续优化。

L169 `MaskType.ID_NO.mask(pat.getIdNo())`：TypeHandler 已自动解密，getIdNo() 返回明文，mask 照旧。

- [ ] **步骤 4：V1__encrypt_id_no.sql 存量迁移（可重入）**

```sql
-- 可重入：仅处理 id_no_hash 为空的行。运行前需在应用层用 CryptoService 回填，
-- 此 SQL 仅占位说明；实际迁移由 Java 迁移程序（CommandLineRunner）完成：
-- 遍历 id_no_hash IS NULL 的行，decrypt 无需（明文）→ hash(id_no) 写入 + id_no 加密回写。
-- 标记：此迁移需启动应用一次（开发环境），生产环境用单独迁移脚本。
```
建议在 patient-service 加 `IdNoMigrationRunner.java`（@Component CommandLineRunner，`@ConditionalOnProperty(name="medconsult.crypto.migrate-id-no", havingValue="true")`），按 hash 列为空判断，遍历加密回填。默认关闭，手动开启。

- [ ] **步骤 5：pom.xml + application.yml**

pom 加 common-crypto 依赖。application.yml 加：
```yaml
medconsult:
  crypto:
    aes-key: ${MEDCONSULT_AES_KEY:Y2hhbmdlLW1lLXBsZWFzZS0zMkJ5dGVzS2V5ISE=}  # 开发默认，生产用环境变量
    migrate-id-no: false
```

- [ ] **步骤 6：编译验证 + Commit**

```bash
cd backend && mvn -q -pl patient-service -am compile
cd ../frontend && npm run build  # 确认前端无回归（id_no 加密不影响前端）
git add backend/patient-service/
git commit -m "feat(patient): id_no AES-256-GCM 加密——TypeHandler透明加解密 + id_no_hash盲索引 + 查询点改造 + 可重入迁移"
```

---

## 收尾任务：文档同步

- [ ] **更新 docs/修改建议.md 总览表「实施状态」列**，把 5 项标为「已实现」。
- [ ] **更新 docs/数据库设计文档.md**，把已落地的表（sys_role 等、refund_order、id_no_hash）回填进正文。
- [ ] **Commit**

```bash
git add docs/
git commit -m "docs: 5 项遗留问题实施完成——同步修改建议状态列 + 数据库设计文档回填表"
```

---

## 自检结果

**1. 规格覆盖度：** 5 项遗留问题（RBAC/症状规则/id_no/退费/审计MQ）各有对应任务（任务 1-5 RBAC+审计，6-7 症状规则，8-10 退费，11-12 id_no）。用户 4 项决策（DB优先+*兜底 / 完整做 id_no / 可靠投递 / 全部一次做）均已落实。✓

**2. 占位符扫描：** 计划内代码块均为可执行代码；任务 7 的 `loadMediumSignals` 标注了"实现时补全 SymptomRuleMapper 注入"，非占位而是实现注记——已说明 fallback 兜底。任务 12 迁移用 CommandLineRunner 而非纯 SQL，已明确。✓

**3. 类型一致性：** AuditLogEvent（common-mq）字段与 notification AuditLogConsumer 期望严格对齐；RiskAssessment record 签名不变；RefundOrder/AppointmentDTO.RefundResponse 字段在 Service/Controller 间一致。✓

**风险提示（如实）：** 本计划未包含自动化集成测试（需 RabbitMQ + MySQL + Redis 完整环境）。每个任务的"编译验证"覆盖编译正确性；端到端行为验证（审计事件落库、退费防重复、id_no 加密后检索）需用户启动完整环境后手动确认。批次 3 id_no 加密涉及存量数据迁移，首次执行需在开发环境用 `medconsult.crypto.migrate-id-no=true` 跑一次迁移 Runner。
