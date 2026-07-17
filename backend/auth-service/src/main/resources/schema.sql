-- ============================================================
-- auth-service 表结构（medconsult_auth schema）
-- 字段与《修改建议》§2.2/§2.3 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- ============================================================

-- sys_user 用户账号表（《数据库设计文档》§2.1）
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    user_no         VARCHAR(32)                           COMMENT '用户编号（业务可读）',
    account         VARCHAR(64)                           COMMENT '账号（手机号/账号至少填一项）',
    phone           VARCHAR(20)                           COMMENT '手机号',
    password_hash   VARCHAR(255)                          COMMENT 'BCrypt 摘要，不可逆',
    name            VARCHAR(50)                           COMMENT '姓名',
    patient_id      BIGINT                                COMMENT '关联患者编号',
    doctor_id       BIGINT                                COMMENT '关联医生编号',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE/DISABLED/LOCKED',
    last_login_at   DATETIME(3)                           COMMENT '最后登录时间',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_account (account),
    UNIQUE KEY uk_sys_user_phone (phone),
    UNIQUE KEY uk_sys_user_user_no (user_no),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号表';

-- sys_service_account 服务账号表（架构文档 §4.2，服务间调用的服务身份凭证）
-- 服务（如 ai-service）用 service_code + api_key 向 auth-service 换发 SERVICE 类型 JWT，
-- 下游服务通过 SecurityContext.requireService() 校验服务身份（§2.4 内部接口鉴权）。
CREATE TABLE IF NOT EXISTS sys_service_account (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    service_code    VARCHAR(64)  NOT NULL                 COMMENT '服务编码（如 ai-service，对应 JwtPayload.serviceCode）',
    service_name    VARCHAR(128)                          COMMENT '服务显示名',
    api_key         VARCHAR(128) NOT NULL                 COMMENT 'API Key（服务换 token 的凭证，BCrypt 摘要存储）',
    api_key_hash    VARCHAR(255) NOT NULL                 COMMENT 'API Key 的 BCrypt 摘要',
    scope           VARCHAR(1024)                         COMMENT '权限点列表（逗号分隔，如 patient:read,drug:read）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_service_account_code (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务账号表';

-- 种子数据：ai-service 服务账号（api_key 明文 = dev-ai-service-api-key，BCrypt cost=10 摘要）
-- 冒烟用固定凭证；生产环境从 KMS/Nacos 注入并轮换。
INSERT IGNORE INTO sys_service_account (id, service_code, service_name, api_key, api_key_hash, scope, status)
VALUES (1001, 'ai-service', 'AI 辅助问诊服务', 'dev-ai-service-api-key',
        '$2a$10$AJrdPShL66fUpfw5MvD0.OWxcW1k7/WjleXjfbArxQ.Y.94LuKaau',
        'patient:read,drug:read,medical-record:read', 'ACTIVE');

-- login_log 登录日志表（《修改建议》§2.2，flow 表不逻辑删除、不 extends BaseEntity）
CREATE TABLE IF NOT EXISTS login_log (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    user_id         BIGINT                                COMMENT '用户 ID（失败时可为空）',
    account         VARCHAR(64)                           COMMENT '尝试登录的账号/手机号',
    role            VARCHAR(32)                           COMMENT '角色（PATIENT/DOCTOR/...）',
    login_type      VARCHAR(20)                           COMMENT 'PASSWORD/REFRESH',
    login_result    VARCHAR(20)                           COMMENT 'SUCCESS/FAILURE/LOCKED',
    ip              VARCHAR(50)                           COMMENT '登录 IP',
    user_agent      VARCHAR(255)                          COMMENT 'UA',
    device_info     VARCHAR(255)                          COMMENT '设备信息',
    login_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    logout_at       DATETIME(3)                           COMMENT '登出时间',
    PRIMARY KEY (id),
    KEY idx_login_log_user_id (user_id),
    KEY idx_login_log_login_at (login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

-- ============================================================
-- RBAC 五表（《修改建议》§2.3，P0 权限模型）
-- sys_user 已删 role 字段（见上），角色移到 sys_user_role，支持一人多角色。
-- 数据范围 data_scope：ALL/DEPT/SELF/ASSIGNED（ASSIGNED=接诊的），本轮建表留结构，
-- 行级数据过滤（PermissionAspect 的 SQL 改写）单独评估，不在本批。
-- ============================================================

-- sys_role 角色表（§2.3 表 1）。注意 AI_SERVICE 不进此表（走 sys_service_account）。
CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    role_code       VARCHAR(32)  NOT NULL                 COMMENT '角色编码：PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN',
    role_name       VARCHAR(50)                           COMMENT '角色名称',
    description     VARCHAR(200)                          COMMENT '描述',
    enabled         TINYINT      NOT NULL DEFAULT 1       COMMENT '是否启用：0 否 1 是',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- sys_permission 权限点表（§2.3 表 2）。本轮建表留空，等接口陆续加 code 时再补种子。
CREATE TABLE IF NOT EXISTS sys_permission (
    id                BIGINT       NOT NULL               COMMENT '主键（雪花 ID）',
    permission_code   VARCHAR(64)  NOT NULL               COMMENT '权限编码，如 patient:read、prescription:review',
    permission_name   VARCHAR(100)                        COMMENT '权限名称',
    resource_type     VARCHAR(50)                         COMMENT '资源类型',
    action            VARCHAR(20)                         COMMENT '操作：read/write/audit/export',
    description       VARCHAR(200)                        COMMENT '描述',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限点表';

-- sys_user_role 用户-角色关联（§2.3 表 4，支持一人多角色）。
CREATE TABLE IF NOT EXISTS sys_user_role (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    user_id         BIGINT       NOT NULL                 COMMENT '用户 ID（sys_user.id）',
    role_id         BIGINT       NOT NULL                 COMMENT '角色 ID（sys_role.id）',
    is_primary      TINYINT      NOT NULL DEFAULT 0       COMMENT '是否主角色：0 否 1 是',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_user (user_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表';

-- sys_role_permission 角色-权限关联（§2.3 表 3，含数据范围）。
-- 本轮无数据（sys_permission 留空），建表留结构供后续权限点接入。
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    role_id         BIGINT       NOT NULL                 COMMENT '角色 ID（sys_role.id）',
    permission_id   BIGINT       NOT NULL                 COMMENT '权限点 ID（sys_permission.id）',
    data_scope      VARCHAR(20)  NOT NULL DEFAULT 'ALL'   COMMENT '数据范围：ALL/DEPT/SELF/ASSIGNED',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';

-- 种子数据：4 个固定角色（为 sys_user_role 提供 role_id 外键来源）。
-- id 用固定值（1-4），便于 DataSeeder 的 sys_user_role 种子按 role_code 反查。
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, enabled) VALUES
    (1, 'PATIENT',         '患者',     '就诊主体，自助建档/挂号/症状自诊', 1),
    (2, 'DOCTOR',          '医生',     '接诊/写病历/开方',                 1),
    (3, 'PHARMACY_ADMIN',  '药师',     '审方/调剂发药/药品库存管理',       1),
    (4, 'HOSPITAL_ADMIN',  '医院管理员', '科室/排班/权限/审计日志管理',     1);
