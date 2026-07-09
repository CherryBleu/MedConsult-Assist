
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGINT PRIMARY KEY,
    session_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    title VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
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
    INDEX idx_ai_medication_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_imaging_detection (
    id BIGINT PRIMARY KEY,
    detection_no VARCHAR(32) NOT NULL UNIQUE,
    patient_id BIGINT NULL,
    record_id BIGINT NULL,
    image_type VARCHAR(50) NOT NULL,
    report_text TEXT NULL,
    image_urls JSON NULL,
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
    reviewed_at DATETIME NULL,
    INDEX idx_ai_imaging_patient (patient_id),
    INDEX idx_ai_imaging_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT PRIMARY KEY,
    feedback_no VARCHAR(32) NOT NULL UNIQUE,
    ai_result_type VARCHAR(50) NOT NULL,
    ai_result_id VARCHAR(64) NOT NULL,
    feedback_by BIGINT NOT NULL,
    useful TINYINT NOT NULL,
    adopted TINYINT NOT NULL DEFAULT 0,
    comment VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_ai_feedback_result (ai_result_type, ai_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT PRIMARY KEY,
    log_no VARCHAR(32) NOT NULL UNIQUE,
    call_type VARCHAR(50) NOT NULL,
    patient_id BIGINT NULL,
    related_id VARCHAR(64) NULL,
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
    INDEX idx_ai_call_log_related (related_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
