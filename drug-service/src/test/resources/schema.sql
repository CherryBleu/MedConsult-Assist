-- ============================================================
-- drug-service 测试用 schema（H2 MySQL 兼容模式）
-- 用途：覆盖主 src/main/resources/schema.sql 的 MySQL 8.0 原生语法，
--      让 DrugFlowTest 在 H2 上跑（Testcontainers 在 Windows Docker Desktop 不可用）。
-- 不入生产部署。H2 MODE=MySQL 不认 KEY/ENGINE/CHARSET/COMMENT，所以这里是简化版。
-- H2 不支持 MySQL JSON 函数：contraindications/interactions 用 TEXT，Service 层当 JSON 串解析仍可工作。
-- ============================================================

CREATE TABLE IF NOT EXISTS drug (
    id                 BIGINT       NOT NULL,
    drug_no            VARCHAR(32)  NOT NULL,
    generic_name       VARCHAR(100),
    trade_name         VARCHAR(100),
    specification      VARCHAR(100),
    dosage_form        VARCHAR(50),
    manufacturer       VARCHAR(200),
    approval_no        VARCHAR(100),
    unit               VARCHAR(20),
    min_stock_threshold INT          DEFAULT 0,
    contraindications  TEXT,                                   -- H2 不用 JSON 类型，TEXT 存 JSON 串
    interactions       TEXT,
    current_stock      INT          DEFAULT 0,
    status             VARCHAR(20)  DEFAULT 'ACTIVE',
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    deleted            INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_drug_no ON drug(drug_no);

CREATE TABLE IF NOT EXISTS drug_stock_batch (
    id              BIGINT        NOT NULL,
    drug_id         BIGINT,
    batch_no        VARCHAR(64)   NOT NULL,
    quantity        INT           DEFAULT 0,
    unit_price      DECIMAL(10,2),
    production_date DATE,
    expire_date     DATE,
    supplier        VARCHAR(200),
    status          VARCHAR(20)   DEFAULT 'AVAILABLE',
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted         INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_batch_no ON drug_stock_batch(batch_no);

CREATE TABLE IF NOT EXISTS drug_stock_flow (
    id                  BIGINT        NOT NULL,
    flow_no             VARCHAR(32)   NOT NULL,
    drug_id             BIGINT,
    batch_id            BIGINT,
    type                VARCHAR(20),
    quantity            INT,
    before_quantity     INT,
    after_quantity      INT,
    related_record_id   BIGINT,
    prescription_id     BIGINT,
    prescription_item_id BIGINT,
    operator_id         BIGINT,
    remark              VARCHAR(500),
    created_at          TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_no ON drug_stock_flow(flow_no);
