-- ============================================================
-- notification-service 表结构（medconsult_notify schema）
-- 字段与《数据库设计文档》§2.11 notification + 《修改建议》§2.2 audit_log 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- 注意：schema 名是 medconsult_notify（infra/01-schemas.sql:14），非文档里的 medconsult_notification
-- ============================================================

-- notification 通知表（《数据库设计文档》§2.11 + 《修改建议》§5.1 补 deleted/updated_at）
CREATE TABLE IF NOT EXISTS notification (
    id              BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    notification_no VARCHAR(32)   NOT NULL                 COMMENT '通知编号（业务可读，如 N202607060001）',
    receiver_id     VARCHAR(32)   NOT NULL                 COMMENT '接收人业务编号（patient_no / doctor_no）',
    receiver_role   VARCHAR(32)   NOT NULL                 COMMENT '接收人角色：PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN',
    type            VARCHAR(32)   NOT NULL                 COMMENT '通知类型：APPOINTMENT/SCHEDULE/MEDICATION/AI_RISK/SYSTEM',
    title           VARCHAR(100)  NOT NULL                 COMMENT '标题',
    content         VARCHAR(1000)                          COMMENT '内容',
    related_type    VARCHAR(50)                            COMMENT '关联业务类型（如 APPOINTMENT/PRESCRIPTION）',
    related_id      VARCHAR(64)                            COMMENT '关联业务编号（如 appointment_no）',
    read_status     TINYINT       NOT NULL DEFAULT 0       COMMENT '是否已读：0 未读 1 已读',
    read_at         DATETIME(3)                            COMMENT '已读时间',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_no (notification_no),
    KEY idx_notification_receiver (receiver_id),
    KEY idx_notification_read (read_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表';

-- audit_log 审计日志表（《修改建议》§2.2）
-- TODO 分表（§2.2 建议）：当前单表。MyBatis-Plus 不原生支持分表，超百万行时改 audit_log_YYYYMM
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    audit_no        VARCHAR(32)   NOT NULL                 COMMENT '审计编号（业务可读）',
    trace_id        VARCHAR(64)                            COMMENT '链路追踪 ID（跨服务串联）',
    resource_type   VARCHAR(50)   NOT NULL                 COMMENT '资源类型：PATIENT/MEDICAL_RECORD/PRESCRIPTION/DRUG/SCHEDULE...',
    resource_id     VARCHAR(64)                            COMMENT '资源业务编号',
    resource_name   VARCHAR(200)                           COMMENT '资源名称冗余（便于检索）',
    action          VARCHAR(20)   NOT NULL                 COMMENT '操作类型：VIEW/CREATE/UPDATE/DELETE/EXPORT/LOGIN/LOGOUT/PAYMENT/CHECK_IN/STATUS_CHANGE/CANCEL 等',
    operator_id     VARCHAR(64)                            COMMENT '操作人 ID',
    operator_role   VARCHAR(32)                            COMMENT '操作人角色',
    operator_name   VARCHAR(50)                            COMMENT '操作人姓名冗余',
    target_owner_id BIGINT                                 COMMENT '资源所属患者 ID（便于按患者检索审计）',
    detail          JSON                                   COMMENT '变更前后快照',
    ip              VARCHAR(50)                            COMMENT '操作 IP',
    user_agent      VARCHAR(255)                           COMMENT 'User-Agent',
    result          VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS/FAILED',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（审计日志只追加，无 updated_at/deleted）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_no (audit_no),
    KEY idx_audit_resource (resource_type, resource_id),
    KEY idx_audit_operator (operator_id),
    KEY idx_audit_action (action),
    KEY idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表（只追加，无逻辑删除）';

-- local_message 本地消息表（架构文档 §6.1 / §3.2）
-- MessageDispatcher 扫描此表投递 RabbitMQ；notification-service 是唯一生产端（依赖 common-mq）。
-- 状态机：PENDING → SENT → CONFIRMED；失败退避重试 SENT → SENT...，超 maxRetry 置 FAILED。
-- 流水语义：只追加 + 状态更新，无逻辑删除（同 audit_log）。
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
