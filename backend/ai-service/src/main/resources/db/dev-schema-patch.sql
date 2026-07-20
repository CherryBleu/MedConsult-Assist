-- Local/development compatibility patch for databases created before the latest
-- ai-service schema changes. It only adds missing columns and is safe to rerun.

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_feedback'
        AND column_name = 'rating') = 0,
    'ALTER TABLE ai_feedback ADD COLUMN rating TINYINT NOT NULL DEFAULT 0 AFTER useful',
    'SELECT ''ai_feedback.rating already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_feedback'
        AND column_name = 'useful') > 0,
    'ALTER TABLE ai_feedback MODIFY COLUMN useful TINYINT NOT NULL DEFAULT 0',
    'SELECT ''ai_feedback.useful is absent'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'caller_service') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN caller_service VARCHAR(50) NULL AFTER related_id',
    'SELECT ''ai_call_log.caller_service already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'trigger_user_id') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN trigger_user_id BIGINT NULL AFTER caller_service',
    'SELECT ''ai_call_log.trigger_user_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'trace_id') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN trace_id VARCHAR(64) NULL AFTER trigger_user_id',
    'SELECT ''ai_call_log.trace_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'cost_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN cost_tokens INT NULL AFTER trace_id',
    'SELECT ''ai_call_log.cost_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'request_id') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN request_id VARCHAR(64) NULL AFTER cost_tokens',
    'SELECT ''ai_call_log.request_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'cache_hit') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN cache_hit TINYINT NOT NULL DEFAULT 0 AFTER request_id',
    'SELECT ''ai_call_log.cache_hit already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'prompt_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN prompt_tokens INT NOT NULL DEFAULT 0 AFTER cache_hit',
    'SELECT ''ai_call_log.prompt_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'completion_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN completion_tokens INT NOT NULL DEFAULT 0 AFTER prompt_tokens',
    'SELECT ''ai_call_log.completion_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'total_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN total_tokens INT NOT NULL DEFAULT 0 AFTER completion_tokens',
    'SELECT ''ai_call_log.total_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'estimated_cost_yuan') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN estimated_cost_yuan DECIMAL(12,6) NOT NULL DEFAULT 0.000000 AFTER total_tokens',
    'SELECT ''ai_call_log.estimated_cost_yuan already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'model_version') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN model_version VARCHAR(50) NULL AFTER model_name',
    'SELECT ''ai_call_log.model_version already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'knowledge_source') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN knowledge_source VARCHAR(100) NULL AFTER model_version',
    'SELECT ''ai_call_log.knowledge_source already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'request_summary') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN request_summary TEXT NULL AFTER knowledge_source',
    'SELECT ''ai_call_log.request_summary already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'response_summary') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN response_summary TEXT NULL AFTER request_summary',
    'SELECT ''ai_call_log.response_summary already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'risk_level') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN risk_level VARCHAR(20) NULL AFTER response_summary',
    'SELECT ''ai_call_log.risk_level already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_medication_analysis'
        AND column_name = 'prescription_id') = 0,
    'ALTER TABLE ai_medication_analysis ADD COLUMN prescription_id BIGINT NULL AFTER record_id',
    'SELECT ''ai_medication_analysis.prescription_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_report_text_analysis'
        AND column_name = 'review_result') = 0,
    'ALTER TABLE ai_report_text_analysis ADD COLUMN review_result VARCHAR(32) NULL AFTER reviewed_by',
    'SELECT ''ai_report_text_analysis.review_result already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND column_name = 'submitted_by_user_id') = 0,
    'ALTER TABLE ai_image_detection ADD COLUMN submitted_by_user_id BIGINT NULL AFTER record_id',
    'SELECT ''ai_image_detection.submitted_by_user_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND column_name = 'submitted_by_service_code') = 0,
    'ALTER TABLE ai_image_detection ADD COLUMN submitted_by_service_code VARCHAR(64) NULL AFTER submitted_by_user_id',
    'SELECT ''ai_image_detection.submitted_by_service_code already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND column_name = 'review_result') = 0,
    'ALTER TABLE ai_image_detection ADD COLUMN review_result VARCHAR(32) NULL AFTER reviewed_by',
    'SELECT ''ai_image_detection.review_result already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_file_upload'
        AND column_name = 'uploaded_by_user_id') = 0,
    'ALTER TABLE ai_file_upload ADD COLUMN uploaded_by_user_id BIGINT NULL AFTER patient_id',
    'SELECT ''ai_file_upload.uploaded_by_user_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_file_upload'
        AND column_name = 'uploaded_by_service_code') = 0,
    'ALTER TABLE ai_file_upload ADD COLUMN uploaded_by_service_code VARCHAR(64) NULL AFTER uploaded_by_user_id',
    'SELECT ''ai_file_upload.uploaded_by_service_code already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'symptom_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE symptom_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER category',
    'SELECT ''symptom_rule.enabled already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'high_risk_symptom_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE high_risk_symptom_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER advice',
    'SELECT ''high_risk_symptom_rule.enabled already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'negative_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE negative_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER match_strategy',
    'SELECT ''negative_rule.enabled already exists'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
