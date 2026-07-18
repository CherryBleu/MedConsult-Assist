-- ============================================================
-- outpatient-service 测试用 schema（H2 MySQL 兼容模式）
-- 用途：覆盖主 src/main/resources/schema.sql 的 MySQL 8.0 原生语法，
--      让 OutpatientFlowTest 在 H2 上跑（Testcontainers 在 Windows Docker Desktop 不可用）。
-- 不入生产部署。H2 MODE=MySQL 不认 KEY/ENGINE/CHARSET/COMMENT，所以这里是简化版。
-- ============================================================

CREATE TABLE IF NOT EXISTS department (
    id            BIGINT       NOT NULL,
    department_no VARCHAR(32)  NOT NULL,
    name          VARCHAR(100),
    description   VARCHAR(500),
    location      VARCHAR(200),
    enabled       INT          DEFAULT 1,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    deleted       INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_department_no ON department(department_no);

CREATE TABLE IF NOT EXISTS doctor (
    id            BIGINT       NOT NULL,
    doctor_no     VARCHAR(32)  NOT NULL,
    name          VARCHAR(50),
    department_id BIGINT,
    title         VARCHAR(50),
    specialties   VARCHAR(500),
    introduction  VARCHAR(1000),
    enabled       INT          DEFAULT 1,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    deleted       INT          DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_doctor_no ON doctor(doctor_no);

CREATE TABLE IF NOT EXISTS doctor_schedule (
    id               BIGINT        NOT NULL,
    schedule_no      VARCHAR(32)   NOT NULL,
    doctor_id        BIGINT,
    department_id    BIGINT,
    schedule_date    DATE,
    period           VARCHAR(20),
    start_time       TIME,
    end_time         TIME,
    total_quota      INT           DEFAULT 0,
    booked_quota     INT           DEFAULT 0,
    registration_fee DECIMAL(10,2),
    status           VARCHAR(20)   DEFAULT 'AVAILABLE',
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    deleted          INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_schedule_doc_date_period ON doctor_schedule(doctor_id, schedule_date, period);

-- doctor_schedule_template 排班模板（H2 兼容版，对齐 src/main/resources/schema.sql）
CREATE TABLE IF NOT EXISTS doctor_schedule_template (
    id                BIGINT        NOT NULL,
    template_no       VARCHAR(32)   NOT NULL,
    doctor_id         BIGINT        NOT NULL,
    department_id     BIGINT        NOT NULL,
    day_of_week       INT           NOT NULL,
    period            VARCHAR(20)   NOT NULL,
    start_time        TIME,
    end_time          TIME,
    total_quota       INT           NOT NULL,
    registration_fee  DECIMAL(10,2),
    enabled           INT           DEFAULT 1,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted           INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_template_no ON doctor_schedule_template(template_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_template_doc_dow_period ON doctor_schedule_template(doctor_id, day_of_week, period);

CREATE TABLE IF NOT EXISTS appointment (
    id                   BIGINT        NOT NULL,
    appointment_no       VARCHAR(32)   NOT NULL,
    patient_id           BIGINT,
    patient_no           VARCHAR(32),
    doctor_id            BIGINT,
    department_id        BIGINT,
    schedule_id          BIGINT,
    appointment_date     DATE,
    period               VARCHAR(20),
    queue_no             INT,
    fee                  DECIMAL(10,2),
    payment_status       VARCHAR(20)   DEFAULT 'UNPAID',
    appointment_status   VARCHAR(20)   DEFAULT 'BOOKED',
    cancel_reason        VARCHAR(255),
    visit_reason         VARCHAR(500),
    source               VARCHAR(20),
    cancel_operator_type VARCHAR(20),
    cancel_operator_id   BIGINT,
    payment_no           VARCHAR(64),
    paid_amount          DECIMAL(10,2),
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    deleted              INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_appointment_no ON appointment(appointment_no);
CREATE INDEX IF NOT EXISTS idx_appointment_patient_schedule ON appointment(patient_no, schedule_id);

-- refund_order 退款单（H2 兼容版，对齐 src/main/resources/schema.sql）
CREATE TABLE IF NOT EXISTS refund_order (
    id                   BIGINT        NOT NULL,
    refund_no            VARCHAR(32)   NOT NULL,
    appointment_id       BIGINT        NOT NULL,
    appointment_no       VARCHAR(32)   NOT NULL,
    payment_no           VARCHAR(64),
    refund_amount        DECIMAL(10,2) NOT NULL,
    refund_type          VARCHAR(20)   DEFAULT 'FULL',
    status               VARCHAR(20)   DEFAULT 'SUCCESS',
    reason               VARCHAR(255),
    operator_type        VARCHAR(20),
    operator_id          BIGINT,
    created_at           TIMESTAMP,
    updated_at           TIMESTAMP,
    deleted              INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_no ON refund_order(refund_no);
