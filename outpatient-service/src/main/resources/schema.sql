-- ============================================================
-- outpatient-service 表结构（medconsult_outpatient schema）
-- 字段与《数据库设计文档》§2.2/§2.3/§2.5/§2.6 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- 补字段约定（《修改建议》§5.1）：所有表补 deleted；appointment 补 source/cancel_operator_*/payment_no/paid_amount
-- ============================================================

-- department 科室表（《数据库设计文档》§2.2 / 《需求文档》§4.3.1）
CREATE TABLE IF NOT EXISTS department (
    id            BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    department_no VARCHAR(32)   NOT NULL                 COMMENT '科室编号（业务可读，如 DEP_CARDIOLOGY）',
    name          VARCHAR(100)  NOT NULL                 COMMENT '科室名称',
    description   VARCHAR(500)                           COMMENT '科室介绍',
    location      VARCHAR(200)                           COMMENT '科室位置',
    enabled       TINYINT       NOT NULL DEFAULT 1       COMMENT '是否启用：1 启用 0 停用',
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted       TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_department_no (department_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室表';

-- doctor 医生表（《数据库设计文档》§2.3 / 《需求文档》§4.3.1）
CREATE TABLE IF NOT EXISTS doctor (
    id            BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    doctor_no     VARCHAR(32)   NOT NULL                 COMMENT '医生编号（业务可读，如 D10001）',
    name          VARCHAR(50)   NOT NULL                 COMMENT '医生姓名',
    department_id BIGINT        NOT NULL                 COMMENT '所属科室 ID',
    title         VARCHAR(50)                            COMMENT '职称（如 主任医师）',
    specialties   VARCHAR(500)                           COMMENT '擅长方向（JSON 数组串，如 ["高血压","心律失常"]）',
    introduction  VARCHAR(1000)                          COMMENT '医生简介',
    enabled       TINYINT       NOT NULL DEFAULT 1       COMMENT '是否启用：1 启用 0 停用',
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted       TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_no (doctor_no),
    KEY idx_doctor_dept (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生表';

-- doctor_schedule 医生排班表（《数据库设计文档》§2.5 / 《需求文档》§4.1.2）
CREATE TABLE IF NOT EXISTS doctor_schedule (
    id               BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    schedule_no      VARCHAR(32)   NOT NULL                 COMMENT '排班编号（业务可读，如 S202607080001）',
    doctor_id        BIGINT        NOT NULL                 COMMENT '医生 ID',
    department_id    BIGINT        NOT NULL                 COMMENT '科室 ID',
    schedule_date    DATE          NOT NULL                 COMMENT '出诊日期',
    period           VARCHAR(20)   NOT NULL                 COMMENT '时段：MORNING/AFTERNOON/EVENING/FULL_DAY',
    start_time       TIME                                   COMMENT '开始时间',
    end_time         TIME                                   COMMENT '结束时间',
    total_quota      INT           NOT NULL DEFAULT 0       COMMENT '总号源',
    booked_quota     INT           NOT NULL DEFAULT 0       COMMENT '已预约号源',
    registration_fee DECIMAL(10,2)                          COMMENT '挂号费',
    status           VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE/FULL/SUSPENDED/CANCELLED',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    -- 同医生同日期同时段不允许重复排班（《需求文档》§4.1.2 规则 1）
    UNIQUE KEY uk_schedule_doc_date_period (doctor_id, schedule_date, period),
    KEY idx_schedule_date (schedule_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生排班表';

-- appointment 预约挂号表（《数据库设计文档》§2.6 / 《需求文档》§4.1.3 / 《修改建议》§5.1 补字段）
CREATE TABLE IF NOT EXISTS appointment (
    id                   BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    appointment_no       VARCHAR(32)   NOT NULL                 COMMENT '预约编号（业务可读，如 A202607060001）',
    patient_id           BIGINT        NOT NULL                 COMMENT '患者 ID（BIGINT 主键）',
    doctor_id            BIGINT        NOT NULL                 COMMENT '医生 ID',
    department_id        BIGINT        NOT NULL                 COMMENT '科室 ID',
    schedule_id          BIGINT        NOT NULL                 COMMENT '排班 ID',
    appointment_date     DATE          NOT NULL                 COMMENT '预约日期',
    period               VARCHAR(20)   NOT NULL                 COMMENT '预约时段（冗余自排班）',
    queue_no             INT           NOT NULL                 COMMENT '就诊序号（该排班内递增）',
    fee                  DECIMAL(10,2)                          COMMENT '挂号费（冗余自排班）',
    payment_status       VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID/PAID/REFUNDING/REFUNDED',
    appointment_status   VARCHAR(20)   NOT NULL DEFAULT 'BOOKED' COMMENT '预约状态：BOOKED/CANCELLED/CHECKED_IN/IN_PROGRESS/COMPLETED/NO_SHOW',
    cancel_reason        VARCHAR(255)                           COMMENT '取消原因',
    visit_reason         VARCHAR(500)                           COMMENT '就诊原因',
    -- 以下 4 字段为《修改建议》§5.1 补充
    source               VARCHAR(20)                            COMMENT '预约来源：MOBILE_APP/OFFICE_WINDOW/SELF_SERVICE',
    cancel_operator_type VARCHAR(20)                            COMMENT '取消操作人类型：PATIENT/DOCTOR/ADMIN',
    cancel_operator_id   BIGINT                                 COMMENT '取消操作人 ID',
    payment_no           VARCHAR(64)                            COMMENT '支付单号',
    paid_amount          DECIMAL(10,2)                          COMMENT '实付金额',
    created_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted              TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointment_no (appointment_no),
    KEY idx_appointment_patient (patient_id),
    KEY idx_appointment_schedule (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约挂号表';
