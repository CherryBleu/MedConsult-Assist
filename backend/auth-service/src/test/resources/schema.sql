-- ============================================================
-- auth-service 测试用 schema（H2 MySQL 兼容模式）
-- 用途：覆盖主 src/main/resources/schema.sql 的 MySQL 8.0 原生语法，
--      让 AuthFlowTest 在 H2 上跑（Testcontainers 在 Windows Docker Desktop 不可用）。
-- 不入生产部署。H2 MODE=MySQL 不认 KEY/ENGINE/CHARSET/COMMENT，所以这里是简化版。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT       NOT NULL,
    user_no         VARCHAR(32),
    account         VARCHAR(64),
    phone           VARCHAR(20),
    password_hash   VARCHAR(255),
    name            VARCHAR(50),
    patient_id      BIGINT,
    doctor_id       BIGINT,
    status          VARCHAR(20)  DEFAULT 'ENABLED',
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_account ON sys_user(account);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_phone ON sys_user(phone);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_user_no ON sys_user(user_no);

CREATE TABLE IF NOT EXISTS login_log (
    id              BIGINT       NOT NULL,
    user_id         BIGINT,
    account         VARCHAR(64),
    role            VARCHAR(32),
    login_type      VARCHAR(20),
    login_result    VARCHAR(20),
    ip              VARCHAR(50),
    user_agent      VARCHAR(255),
    device_info     VARCHAR(255),
    login_at        TIMESTAMP,
    logout_at       TIMESTAMP,
    PRIMARY KEY (id)
);

-- RBAC 四表（H2 兼容版，对齐 src/main/resources/schema.sql）。AI_SERVICE 不进 sys_role。
CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT       NOT NULL,
    role_code       VARCHAR(32)  NOT NULL,
    role_name       VARCHAR(50),
    description     VARCHAR(200),
    enabled         INT          DEFAULT 1,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_code ON sys_role(role_code);

CREATE TABLE IF NOT EXISTS sys_permission (
    id                BIGINT       NOT NULL,
    permission_code   VARCHAR(64)  NOT NULL,
    permission_name   VARCHAR(100),
    resource_type     VARCHAR(50),
    action            VARCHAR(20),
    description       VARCHAR(200),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted           INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_permission_code ON sys_permission(permission_code);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id              BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    is_primary      INT          DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_role ON sys_user_role(user_id, role_id);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id              BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    permission_id   BIGINT       NOT NULL,
    data_scope      VARCHAR(20)  DEFAULT 'ALL',
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_permission ON sys_role_permission(role_id, permission_id);

-- 种子：4 个固定角色（H2 upsert 用 MERGE ... KEY(id)，确保可重入）
MERGE INTO sys_role (id, role_code, role_name, description, enabled) KEY(id) VALUES
    (1, 'PATIENT',         '患者',     '就诊主体', 1),
    (2, 'DOCTOR',          '医生',     '接诊/开方', 1),
    (3, 'PHARMACY_ADMIN',  '药师',     '审方/发药', 1),
    (4, 'HOSPITAL_ADMIN',  '医院管理员', '科室/排班/审计', 1);
