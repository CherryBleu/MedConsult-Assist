-- ============================================================
-- patient-service 表结构（medconsult_patient schema）
-- 字段与《数据库设计文档》§2.4 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- ============================================================

-- patient 患者档案表（《数据库设计文档》§2.4 / 《需求文档》§4.1.1）
CREATE TABLE IF NOT EXISTS patient (
    id                    BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    patient_no            VARCHAR(32)                           COMMENT '患者编号（业务可读，如 P202607060001）',
    name                  VARCHAR(50)  NOT NULL                 COMMENT '患者姓名',
    gender                VARCHAR(20)                           COMMENT '性别：MALE/FEMALE/UNKNOWN',
    birth_date            DATE                                  COMMENT '出生日期',
    id_type               VARCHAR(30)                           COMMENT '证件类型：ID_CARD/PASSPORT/OTHER',
    -- TODO 字段级加密：id_no 应做 AES-256 落库（《修改建议》§5.3）。
    --      当前无现成加密工具（common-security 暂未提供对称加密组件），先明文存储，
    --      待 common 加密模块就绪后改为密文存储 + 查询时解密脱敏。
    id_no                 VARCHAR(64)                           COMMENT '证件号（预留加密长度，TODO AES-256）',
    phone                 VARCHAR(20)                           COMMENT '手机号',
    address               VARCHAR(255)                          COMMENT '地址',
    allergies             TEXT                                  COMMENT '过敏史（JSON 数组串，如 ["青霉素","头孢类"]）',
    past_medical_history  TEXT                                  COMMENT '既往病史（JSON 数组串）',
    family_history        TEXT                                  COMMENT '家族病史（JSON 数组串）',
    emergency_contact     VARCHAR(500)                          COMMENT '紧急联系人（JSON 串，如 {"name":"李四","relation":"配偶","phone":"13800000002"}）',
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '档案状态：ACTIVE/DISABLED/MERGED',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted               TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_no (patient_no),
    UNIQUE KEY uk_patient_id_card (id_type, id_no),
    KEY idx_patient_status (status),
    KEY idx_patient_phone (phone),
    KEY idx_patient_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者档案表';
