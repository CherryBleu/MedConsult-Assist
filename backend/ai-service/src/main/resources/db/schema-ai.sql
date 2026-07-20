
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGINT PRIMARY KEY,
    session_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    title VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_risk_level VARCHAR(20) NULL,
    context_symptoms JSON NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_ai_chat_session_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    user_message TEXT NOT NULL,
    ai_answer TEXT NOT NULL,
    citations JSON NULL,
    suggested_departments JSON NULL,
    risk_level VARCHAR(20) NULL,
    emergency_advice TINYINT NOT NULL DEFAULT 0,
    model_name VARCHAR(100) NULL,
    query_embedding_model VARCHAR(50) NULL,
    rule_version VARCHAR(20) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_chat_message_session (session_id),
    INDEX idx_ai_chat_message_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_triage_result (
    id BIGINT PRIMARY KEY,
    triage_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    symptoms JSON NOT NULL,
    duration VARCHAR(100) NULL,
    severity VARCHAR(20) NULL,
    recommendations JSON NOT NULL,
    emergency_recommended TINYINT NOT NULL DEFAULT 0,
    citations JSON NULL,
    model_name VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_triage_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_medical_summary (
    id BIGINT PRIMARY KEY,
    summary_no VARCHAR(32) NOT NULL UNIQUE,
    record_id BIGINT NOT NULL,
    summary_type VARCHAR(20) NOT NULL,
    summary_content JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NULL,
    prompt_version VARCHAR(50) NULL,
    generated_at DATETIME NOT NULL,
    confirmed_by BIGINT NULL,
    confirmed_at DATETIME NULL,
    INDEX idx_ai_summary_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_medication_analysis (
    id BIGINT PRIMARY KEY,
    analysis_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    record_id BIGINT NULL,
    prescription_id BIGINT NULL,
    prescriptions JSON NOT NULL,
    overall_risk_level VARCHAR(20) NOT NULL,
    allergy_risks JSON NULL,
    contraindication_risks JSON NULL,
    interaction_risks JSON NULL,
    reminders JSON NULL,
    function_trace JSON NULL,
    model_name VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_medication_patient (patient_id),
    INDEX idx_ai_medication_record (record_id),
    INDEX idx_ai_medication_prescription (prescription_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_report_text_analysis (
    id BIGINT PRIMARY KEY,
    analysis_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    record_id BIGINT NULL,
    attachment_id BIGINT NULL,
    report_type VARCHAR(50) NOT NULL,
    report_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    abnormal_detected TINYINT NOT NULL DEFAULT 0,
    findings JSON NULL,
    external_provider VARCHAR(100) NULL,
    external_model VARCHAR(100) NULL,
    latency_ms INT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'UNREVIEWED',
    reviewed_by BIGINT NULL,
    review_result VARCHAR(32) NULL,
    review_comment VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    reviewed_at DATETIME NULL,
    INDEX idx_ai_report_patient (patient_id),
    INDEX idx_ai_report_record (record_id),
    INDEX idx_ai_report_attachment (attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_image_detection (
    id BIGINT PRIMARY KEY,
    detection_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    record_id BIGINT NULL,
    submitted_by_user_id BIGINT NULL,
    submitted_by_service_code VARCHAR(64) NULL,
    image_type VARCHAR(50) NOT NULL,
    image_urls JSON NULL,
    storage_type VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL,
    abnormal_detected TINYINT NOT NULL DEFAULT 0,
    findings JSON NULL,
    external_provider VARCHAR(100) NULL,
    external_model VARCHAR(100) NULL,
    latency_ms INT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'UNREVIEWED',
    reviewed_by BIGINT NULL,
    review_result VARCHAR(32) NULL,
    review_comment VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    reviewed_at DATETIME NULL,
    INDEX idx_ai_image_patient (patient_id),
    INDEX idx_ai_image_record (record_id),
    INDEX idx_ai_image_submitter_user (submitted_by_user_id),
    INDEX idx_ai_image_submitter_service (submitted_by_service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT PRIMARY KEY,
    feedback_no VARCHAR(32) NOT NULL UNIQUE,
    ai_result_type VARCHAR(50) NOT NULL,
    ai_result_id VARCHAR(64) NOT NULL,
    feedback_by BIGINT NOT NULL,
    useful TINYINT NOT NULL DEFAULT 0,
    rating TINYINT NOT NULL DEFAULT 0,
    adopted TINYINT NOT NULL DEFAULT 0,
    comment VARCHAR(1000) NULL,
    admin_reply VARCHAR(1000) NULL,
    replied_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_feedback_result (ai_result_type, ai_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_file_upload (
    id BIGINT PRIMARY KEY,
    file_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    uploaded_by_user_id BIGINT NULL,
    uploaded_by_service_code VARCHAR(64) NULL,
    record_id BIGINT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    storage_type VARCHAR(20) NOT NULL DEFAULT 'MINIO',
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    upload_mode VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    chunk_upload_id VARCHAR(64) NULL,
    total_chunks INT NULL,
    uploaded_chunks INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_ai_file_object (bucket, object_key),
    INDEX idx_ai_file_patient (patient_id),
    INDEX idx_ai_file_uploader_user (uploaded_by_user_id),
    INDEX idx_ai_file_uploader_service (uploaded_by_service_code),
    INDEX idx_ai_file_record (record_id),
    INDEX idx_ai_file_chunk_upload (chunk_upload_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT PRIMARY KEY,
    log_no VARCHAR(32) NOT NULL UNIQUE,
    call_type VARCHAR(50) NOT NULL,
    patient_id BIGINT NULL,
    related_id VARCHAR(64) NULL,
    caller_service VARCHAR(50) NULL,
    trigger_user_id BIGINT NULL,
    trace_id VARCHAR(64) NULL,
    cost_tokens INT NULL,
    request_id VARCHAR(64) NULL,
    cache_hit TINYINT NOT NULL DEFAULT 0,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    estimated_cost_yuan DECIMAL(12,6) NOT NULL DEFAULT 0.000000,
    model_name VARCHAR(100) NULL,
    model_version VARCHAR(50) NULL,
    knowledge_source VARCHAR(100) NULL,
    request_summary TEXT NULL,
    response_summary TEXT NULL,
    risk_level VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL,
    latency_ms INT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_call_log_patient_type (patient_id, call_type),
    INDEX idx_ai_call_log_related (related_id),
    UNIQUE KEY uk_ai_call_log_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS symptom_rule (
    id BIGINT PRIMARY KEY,
    keyword VARCHAR(50) NOT NULL,
    standard_symptom VARCHAR(50) NOT NULL,
    category VARCHAR(50) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_symptom_rule_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS high_risk_symptom_rule (
    id BIGINT PRIMARY KEY,
    symptom_combo JSON NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    advice VARCHAR(500) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS negative_rule (
    id BIGINT PRIMARY KEY,
    negative_words JSON NOT NULL,
    match_strategy VARCHAR(20) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- local_message 本地消息表（架构文档 §6.1 / §3.2）
-- MessageDispatcher（common-mq 自动装配）扫描此表投递 RabbitMQ；
-- ai-service 是影像检测任务的 MQ 生产端，需要此表做可靠投递。
-- 状态机：PENDING → SENT → CONFIRMED；失败退避重试 SENT → SENT...，超 maxRetry 置 FAILED。
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

