-- ============================================================
-- medical-record-service 表结构（medconsult_record schema）
-- 字段与《数据库设计文档》§2.7 medical_record + 《修改建议》§2.1 prescription/prescription_item 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- 修订项（§2.1 权威）：处方从 medical_record 剥离独立成表；
--   medical_record.prescriptions_snapshot 降级为只读快照（归档时拍一份，不再作为处方数据来源）
-- ============================================================

-- medical_record 电子病历表（《数据库设计文档》§2.7 / 《需求文档》§4.1.4 / 《修改建议》§2.1 调整）
CREATE TABLE IF NOT EXISTS medical_record (
    id                    BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    record_no             VARCHAR(32)   NOT NULL                 COMMENT '病历编号（业务可读，如 MR202607060001）',
    patient_id            BIGINT        NOT NULL                 COMMENT '患者 ID（业务编号正哈希占位，下一批接 Feign 后为真实主键）',
    doctor_id             BIGINT        NOT NULL                 COMMENT '医生 ID（业务编号正哈希占位）',
    appointment_id        BIGINT                                 COMMENT '预约 ID（可空，复诊/急诊可能无预约）',
    chief_complaint       VARCHAR(1000)                          COMMENT '主诉',
    present_illness       TEXT                                   COMMENT '现病史',
    past_history          TEXT                                   COMMENT '既往史',
    physical_exam         TEXT                                   COMMENT '体格检查',
    initial_diagnosis     TEXT                                   COMMENT '初步诊断（JSON 数组串，如 ["心律失常待查","高血压"]）',
    final_diagnosis       TEXT                                   COMMENT '最终诊断（归档时填写，JSON 数组串）',
    prescriptions_snapshot TEXT                                  COMMENT '处方只读快照（修订项 §2.1：降级为快照；batch 1 留 null，batch 2 归档时按需填充）',
    doctor_advice         TEXT                                   COMMENT '医嘱',
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ARCHIVED/REVISED',
    archived_at           DATETIME(3)                            COMMENT '归档时间（status=ARCHIVED 时填）',
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted               TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_no (record_no),
    KEY idx_medical_record_patient (patient_id),
    KEY idx_medical_record_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电子病历表';

-- prescription 处方主表（《修改建议》§2.1 表 1，从 medical_record 剥离独立成表）
CREATE TABLE IF NOT EXISTS prescription (
    id                      BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    prescription_no         VARCHAR(32)   NOT NULL                 COMMENT '处方编号（业务可读，如 RX202607060001）',
    record_id               BIGINT        NOT NULL                 COMMENT '关联病历 ID（medical_record.id）',
    patient_id              BIGINT        NOT NULL                 COMMENT '患者 ID（业务编号正哈希占位）',
    doctor_id               BIGINT        NOT NULL                 COMMENT '开方医生 ID（业务编号正哈希占位）',
    department_id           BIGINT                                 COMMENT '开方科室 ID（可空）',
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态机：DRAFT/PENDING_REVIEW/APPROVED/REJECTED/PAID/DISPENSED/COMPLETED/CANCELLED',
    pharmacy_pharmacist_id  BIGINT                                 COMMENT '审方药师 ID（review 时回填）',
    reviewed_at             DATETIME(3)                            COMMENT '审方时间',
    review_comment          VARCHAR(500)                           COMMENT '审方意见（approve/reject 均可填）',
    reject_reason           VARCHAR(500)                           COMMENT '驳回原因（reject 时填）',
    total_fee               DECIMAL(10,2)                          COMMENT '处方总金额（明细 subtotal 累加）',
    paid_amount             DECIMAL(10,2)                          COMMENT '实付金额（pay 时填，第 2 批）',
    payment_no              VARCHAR(64)                            COMMENT '支付单号（外部支付系统回传，pay 时回填，便于对账/退款）',
    payment_status          VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID/PAID/REFUNDED',
    source                  VARCHAR(20)   NOT NULL DEFAULT 'OUTPATIENT' COMMENT '处方来源：OUTPATIENT/INPATIENT/EMERGENCY',
    created_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted                 TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prescription_no (prescription_no),
    KEY idx_prescription_record (record_id),
    KEY idx_prescription_patient (patient_id),
    KEY idx_prescription_status (status),
    KEY idx_prescription_pharmacist (pharmacy_pharmacist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处方主表';

-- prescription_item 处方明细表（《修改建议》§2.1 表 2）
CREATE TABLE IF NOT EXISTS prescription_item (
    id                    BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    prescription_id       BIGINT        NOT NULL                 COMMENT '处方 ID（prescription.id）',
    drug_id               BIGINT                                 COMMENT '药品 ID（预留，本批 null；dispense 用 drug_no 调 drug-service）',
    drug_no               VARCHAR(32)                            COMMENT '药品编号 drug_no（开方时存，dispense 时用它调 drug-service outbound）',
    drug_name_snapshot    VARCHAR(100)  NOT NULL                 COMMENT '药品名快照（防药品库变更影响历史处方）',
    specification_snapshot VARCHAR(100)                           COMMENT '规格快照',
    dosage                VARCHAR(50)                            COMMENT '单次剂量（如 30mg）',
    frequency             VARCHAR(50)                            COMMENT '频次（如 每日一次）',
    route                 VARCHAR(50)                            COMMENT '给药途径（如 口服，可空）',
    days                  INT                                   COMMENT '天数',
    quantity              DECIMAL(10,2)                          COMMENT '总数量',
    unit                  VARCHAR(20)                            COMMENT '单位（如 片、盒）',
    unit_price            DECIMAL(10,2)                          COMMENT '单价（本批可空，前端传或留 0）',
    subtotal              DECIMAL(10,2)                          COMMENT '小计 = quantity × unit_price',
    allocated_batch_id    BIGINT                                 COMMENT '调剂后锁定的批次 ID（FEFO 选批后回填，第 2 批；本批 null）',
    dispensed_quantity    DECIMAL(10,2)                          COMMENT '已发数量（调剂发药后回填，第 2 批；本批 null）',
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted               TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    KEY idx_prescription_item_prescription (prescription_id),
    KEY idx_prescription_item_drug (drug_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处方明细表';

-- attachment 附件表（《数据库设计文档》§2.12，docs/接口文档.md §2.8.4/2.8.5）
-- 保存病历/检查报告/影像报告等文件元数据，不约束文件实际存储方式（实际文件走 /files/upload）。
CREATE TABLE IF NOT EXISTS attachment (
    id                    BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    attachment_no         VARCHAR(32)   NOT NULL                 COMMENT '附件编号（ATT + 雪花 base36）',
    biz_type              VARCHAR(50)   NOT NULL                 COMMENT '业务类型：MEDICAL_RECORD/EXAM_REPORT/IMAGING_REPORT',
    biz_id                VARCHAR(64)   NOT NULL                 COMMENT '业务编号（如 record_no）',
    file_name             VARCHAR(255)                           COMMENT '文件名',
    file_type             VARCHAR(50)                            COMMENT '文件类型（PDF/DICOM/JPG 等）',
    file_url              VARCHAR(1000)                          COMMENT '文件访问地址',
    file_size             BIGINT                                 COMMENT '文件大小（Byte）',
    uploaded_by           BIGINT                                 COMMENT '上传人 ID（sys_user.id）',
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted               TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attachment_no (attachment_no),
    KEY idx_attachment_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件元数据表';

-- 本地消息表（审计生产端 @AuditLog 经此可靠投递到 audit.log 队列，对齐 notification-service schema.sql）
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
