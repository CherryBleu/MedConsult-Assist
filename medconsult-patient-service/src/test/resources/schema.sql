-- ============================================================
-- patient-service 测试用 schema（H2 MySQL 兼容模式）
-- 用途：覆盖主 src/main/resources/schema.sql 的 MySQL 8.0 原生语法，
--      让 PatientFlowTest 在 H2 上跑（Testcontainers 在 Windows Docker Desktop 不可用）。
-- 不入生产部署。H2 MODE=MySQL 不认 KEY/ENGINE/CHARSET/COMMENT，所以这里是简化版。
-- ============================================================

CREATE TABLE IF NOT EXISTS patient (
    id                    BIGINT       NOT NULL,
    patient_no            VARCHAR(32),
    name                  VARCHAR(50),
    gender                VARCHAR(20),
    birth_date            DATE,
    id_type               VARCHAR(30),
    id_no                 VARCHAR(64),
    phone                 VARCHAR(20),
    address               VARCHAR(255),
    allergies             TEXT,
    past_medical_history  TEXT,
    family_history        TEXT,
    emergency_contact     VARCHAR(500),
    status                VARCHAR(20)  DEFAULT 'ACTIVE',
    created_at            TIMESTAMP,
    updated_at            TIMESTAMP,
    deleted               INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_no ON patient(patient_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_id_card ON patient(id_type, id_no);
