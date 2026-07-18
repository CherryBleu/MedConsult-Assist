-- ============================================================
-- medical-record-service 测试用 schema（H2 MySQL 兼容模式）
-- 用途：覆盖主 src/main/resources/schema.sql 的 MySQL 8.0 原生语法，
--      让 MedicalRecordFlowTest 在 H2 上跑（Testcontainers 在 Windows Docker Desktop 不可用）。
-- 不入生产部署。H2 MODE=MySQL 不认 KEY/ENGINE/CHARSET/COMMENT，所以这里是简化版。
-- ============================================================

CREATE TABLE IF NOT EXISTS medical_record (
    id                    BIGINT        NOT NULL,
    record_no             VARCHAR(32)   NOT NULL,
    patient_id            BIGINT        NOT NULL,
    doctor_id             BIGINT        NOT NULL,
    appointment_id        BIGINT,
    chief_complaint       VARCHAR(1000),
    present_illness       CLOB,
    past_history          CLOB,
    physical_exam         CLOB,
    initial_diagnosis     CLOB,
    final_diagnosis       CLOB,
    prescriptions_snapshot CLOB,
    doctor_advice         CLOB,
    status                VARCHAR(20)   DEFAULT 'DRAFT',
    archived_at           TIMESTAMP,
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP,
    deleted               INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_record_no ON medical_record(record_no);

CREATE TABLE IF NOT EXISTS prescription (
    id                      BIGINT        NOT NULL,
    prescription_no         VARCHAR(32)   NOT NULL,
    record_id               BIGINT        NOT NULL,
    patient_id              BIGINT        NOT NULL,
    doctor_id               BIGINT        NOT NULL,
    department_id           BIGINT,
    status                  VARCHAR(20)   DEFAULT 'DRAFT',
    pharmacy_pharmacist_id  BIGINT,
    reviewed_at             TIMESTAMP,
    review_comment          VARCHAR(500),
    reject_reason           VARCHAR(500),
    total_fee               DECIMAL(10,2),
    paid_amount             DECIMAL(10,2),
    payment_no              VARCHAR(64),
    payment_status          VARCHAR(20)   DEFAULT 'UNPAID',
    source                  VARCHAR(20)   DEFAULT 'OUTPATIENT',
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP,
    deleted                 INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_prescription_no ON prescription(prescription_no);

CREATE TABLE IF NOT EXISTS prescription_item (
    id                     BIGINT        NOT NULL,
    prescription_id        BIGINT        NOT NULL,
    drug_id                BIGINT,
    drug_no                VARCHAR(32),
    drug_name_snapshot     VARCHAR(100)  NOT NULL,
    specification_snapshot VARCHAR(100),
    dosage                 VARCHAR(50),
    frequency              VARCHAR(50),
    route                  VARCHAR(50),
    days                   INT,
    quantity               DECIMAL(10,2),
    unit                   VARCHAR(20),
    unit_price             DECIMAL(10,2),
    subtotal               DECIMAL(10,2),
    allocated_batch_id     BIGINT,
    dispensed_quantity     DECIMAL(10,2),
    created_at             TIMESTAMP,
    updated_at             TIMESTAMP,
    deleted                INT           DEFAULT 0,
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
