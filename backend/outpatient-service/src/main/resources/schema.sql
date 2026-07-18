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
    patient_no           VARCHAR(32)                            COMMENT '患者编号（冗余自 patient-service，便于按业务编号查询/过滤）',
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
    KEY idx_appointment_patient_no (patient_no),
    KEY idx_appointment_schedule (schedule_id),
    -- 重复预约校验（createInTx）：WHERE patient_no=? AND schedule_id=? AND appointment_status != 'CANCELLED'
    -- 单列 idx_appointment_patient_no 能过滤 patient_no，但 schedule_id 部分回表；
    -- 热门专家号同患者多次抢号时，复合索引避免回表扫描。
    KEY idx_appointment_patient_schedule (patient_no, schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约挂号表';

-- refund_order 退款单表（挂号费退款，任务 11：退款状态机与幂等）
CREATE TABLE IF NOT EXISTS refund_order (
    id              BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    refund_no       VARCHAR(32)   NOT NULL                 COMMENT '退款单号（业务可读，如 Rxxxxx）',
    appointment_id  BIGINT        NOT NULL                 COMMENT '预约 ID',
    appointment_no  VARCHAR(32)   NOT NULL                 COMMENT '预约编号',
    patient_id      BIGINT        NOT NULL                 COMMENT '患者 ID',
    refund_amount   DECIMAL(10,2) NOT NULL                 COMMENT '退款金额',
    provider        VARCHAR(20)   NOT NULL                 COMMENT '退款渠道提供方：MOCK/WECHAT/ALIPAY',
    channel         VARCHAR(20)   NOT NULL                 COMMENT '退款方式：ORIGINAL/MANUAL',
    idempotency_key VARCHAR(128)                           COMMENT '客户端幂等键',
    reason          VARCHAR(255)                           COMMENT '退款原因',
    failure_reason  VARCHAR(255)                           COMMENT '失败原因',
    status          VARCHAR(20)   NOT NULL                 COMMENT '退款状态：PROCESSING/SUCCEEDED/FAILED',
    requested_at    DATETIME(3)   NOT NULL                 COMMENT '发起时间',
    processed_at    DATETIME(3)                            COMMENT '处理时间',
    succeeded_at    DATETIME(3)                            COMMENT '成功时间',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_refund_appointment (appointment_id),
    KEY idx_refund_appointment_no (appointment_no),
    KEY idx_refund_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款单表';

-- ============================================================
-- 种子数据（冒烟/演示用；固定主键保证跨服务引用一致）
-- 科室 department_no 严格对齐 ai-service TriageService.departmentIdOf 的硬编码常量，
-- 否则智能分诊"去挂号"链路因查不到科室返回业务异常。
-- ============================================================

-- 科室（主键 1001-1005）
INSERT IGNORE INTO department (id, department_no, name, description, location, enabled) VALUES
    (1001, 'DEP_CARDIOLOGY',   '心内科',     '心血管疾病诊疗，含高血压、冠心病、心律失常', '门诊楼 3 楼 A 区', 1),
    (1002, 'DEP_RESPIRATORY',  '呼吸科',     '呼吸系统疾病诊疗，含哮喘、肺炎、慢阻肺',     '门诊楼 3 楼 B 区', 1),
    (1003, 'DEP_PEDIATRICS',   '儿科',       '0-14 岁儿童常见病、多发病诊疗',              '门诊楼 2 楼 C 区', 1),
    (1004, 'DEP_EMERGENCY',    '急诊科',     '24 小时急危重症救治',                        '急诊楼 1 楼',      1),
    (1005, 'DEP_GENERAL',      '全科医学科', '常见病、多发病及未明确专科疾病的初诊',       '门诊楼 1 楼 D 区', 1);

-- 医生（主键 2001-2003；department_id 指向上面科室主键）
INSERT IGNORE INTO doctor (id, doctor_no, name, department_id, title, specialties, introduction, enabled) VALUES
    (2001, 'D20001', '张心明', 1001, '主任医师', '["高血压","冠心病","心律失常"]',
     '从医 20 年，擅长心血管疑难重症诊治，完成各类心脏介入手术逾千例。', 1),
    (2002, 'D20002', '李呼吸', 1002, '副主任医师', '["哮喘","慢阻肺","肺部感染"]',
     '呼吸与危重症医学科副主任医师，擅长慢性气道疾病规范化管理。', 1),
    (2003, 'D20003', '王小儿', 1003, '主治医师', '["儿童呼吸道感染","小儿贫血"]',
     '儿科主治医师，擅长儿童常见病诊治与儿童保健。', 1);

-- 排班（主键 4001-4003；未来 7 天各一位医生上午出诊）
-- schedule_no 用固定常量；日期用 CURDATE() + INTERVAL 保证始终未来日期
INSERT IGNORE INTO doctor_schedule (id, schedule_no, doctor_id, department_id, schedule_date, period, start_time, end_time, total_quota, booked_quota, registration_fee, status)
SELECT id, schedule_no, doctor_id, department_id, TIMESTAMPADD(DAY, day_offset, CURRENT_DATE), period, start_time, end_time, total_quota, booked_quota, registration_fee, status
FROM (
    SELECT 4001 AS id, 'S40001' AS schedule_no, 2001 AS doctor_id, 1001 AS department_id, 1 AS day_offset, 'MORNING' AS period, '08:00:00' AS start_time, '12:00:00' AS end_time, 20 AS total_quota, 0 AS booked_quota, 50.00 AS registration_fee, 'AVAILABLE' AS status
    UNION ALL SELECT 4002, 'S40002', 2002, 1002, 2, 'MORNING', '08:00:00', '12:00:00', 20, 0, 40.00, 'AVAILABLE'
    UNION ALL SELECT 4003, 'S40003', 2003, 1003, 3, 'AFTERNOON', '14:00:00', '17:00:00', 15, 0, 30.00, 'AVAILABLE'
) seed
WHERE NOT EXISTS (SELECT 1 FROM doctor_schedule WHERE schedule_no IN ('S40001','S40002','S40003'));

CREATE TABLE IF NOT EXISTS local_message (
    id              BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    message_no      VARCHAR(64)   NOT NULL                 COMMENT '业务唯一键（消费者幂等去重用）',
    exchange        VARCHAR(100)                           COMMENT '目标交换机',
    routing_key     VARCHAR(100)                           COMMENT '路由键',
    payload_json    TEXT                                   COMMENT '消息载荷（JSON 字符串）',
    status          VARCHAR(20)   NOT NULL                 COMMENT '状态：PENDING/SENT/CONFIRMED/FAILED',
    retry_count     INT           NOT NULL DEFAULT 0       COMMENT '已重试次数',
    next_retry_at   DATETIME(3)                            COMMENT '下次重试时间（退避调度用）',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_local_message_no (message_no),
    KEY idx_local_message_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地消息表（可靠投递，MessageDispatcher 扫描）';
