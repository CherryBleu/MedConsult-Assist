-- ============================================================
-- notification-service 测试用 schema（H2 MySQL 兼容模式）
-- 用途：覆盖主 src/main/resources/schema.sql 的 MySQL 8.0 原生语法，
--      让测试在 H2 上跑（Testcontainers 在 Windows Docker Desktop 不可用）。
-- 另含 local_message 表（common-mq AutoConfiguration 注入 MessageDispatcher 依赖它）。
-- ============================================================

CREATE TABLE IF NOT EXISTS notification (
    id              BIGINT        NOT NULL,
    notification_no VARCHAR(32)   NOT NULL,
    receiver_id     VARCHAR(32)   NOT NULL,
    receiver_role   VARCHAR(32)   NOT NULL,
    type            VARCHAR(32)   NOT NULL,
    title           VARCHAR(100)  NOT NULL,
    content         VARCHAR(1000),
    related_type    VARCHAR(50),
    related_id      VARCHAR(64),
    read_status     INT           DEFAULT 0,
    read_at         TIMESTAMP,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_no ON notification(notification_no);

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT        NOT NULL,
    audit_no        VARCHAR(32)   NOT NULL,
    trace_id        VARCHAR(64),
    resource_type   VARCHAR(50)   NOT NULL,
    resource_id     VARCHAR(64),
    resource_name   VARCHAR(200),
    action          VARCHAR(20)   NOT NULL,
    operator_id     VARCHAR(64),
    operator_role   VARCHAR(32),
    operator_name   VARCHAR(50),
    target_owner_id BIGINT,
    detail          CLOB,
    ip              VARCHAR(50),
    user_agent      VARCHAR(255),
    result          VARCHAR(20)   DEFAULT 'SUCCESS',
    created_at      TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_audit_no ON audit_log(audit_no);

-- local_message 表（common-mq AutoConfiguration 注入 MessageDispatcher 依赖）
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
