-- Upgrade an existing MedConsult AI database after the AI architecture split.
-- Run once against the database used by ai/src/main/resources/application.yml.

ALTER TABLE ai_chat_session
    ADD COLUMN last_risk_level VARCHAR(20) NULL,
    ADD COLUMN context_symptoms JSON NULL;

ALTER TABLE ai_chat_message
    ADD COLUMN query_embedding_model VARCHAR(50) NULL,
    ADD COLUMN rule_version VARCHAR(20) NULL;

ALTER TABLE ai_medication_analysis
    ADD COLUMN prescription_id BIGINT NULL;

CREATE INDEX idx_ai_medication_prescription ON ai_medication_analysis (prescription_id);

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
    review_comment VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    reviewed_at DATETIME NULL,
    INDEX idx_ai_image_patient (patient_id),
    INDEX idx_ai_image_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ai_call_log
    ADD COLUMN caller_service VARCHAR(50) NULL,
    ADD COLUMN trigger_user_id BIGINT NULL,
    ADD COLUMN trace_id VARCHAR(64) NULL,
    ADD COLUMN cost_tokens INT NULL,
    ADD COLUMN request_id VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_ai_call_log_request ON ai_call_log (request_id);

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
