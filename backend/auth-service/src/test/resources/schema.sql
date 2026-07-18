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

CREATE TABLE IF NOT EXISTS local_message (
    id              BIGINT       NOT NULL,
    message_no      VARCHAR(64),
    exchange        VARCHAR(100),
    routing_key     VARCHAR(100),
    payload_json    CLOB,
    status          VARCHAR(20),
    retry_count     INT,
    next_retry_at   TIMESTAMP,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_local_message_no ON local_message(message_no);
