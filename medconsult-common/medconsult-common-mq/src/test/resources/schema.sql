-- H2 MySQL 模式 schema，供 MqFlowTest 验证 local_message 持久化 + 投递状态流转。
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
