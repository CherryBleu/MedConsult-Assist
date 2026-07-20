-- ============================================================
-- AI 服务三处增量迁移合并脚本（2026-07-20）
-- ============================================================
--
-- 适用场景：本地或测试库的 medconsult_ai schema 是较早建出来的，
-- 缺失以下三组已并入 schema-ai.sql 但旧库没跑过的字段。
-- 新建库直接用 schema-ai.sql 即可，无需本脚本。
--
-- 合并来源（按依赖顺序执行）：
--   1. upgrade-ai-call-log-observability-20260718.sql  —— ai_call_log 可观测性字段
--   2. upgrade-ai-imaging-security-20260718.sql         —— ai_image_detection 影像归属与复核字段
--   3. upgrade-ai-rule-enabled-20260719.sql             —— 三张症状规则表 enabled 字段
--
-- 本脚本幂等：每条 ALTER 都先查 information_schema.columns，已存在则跳过，
-- 可重复执行；列上已存在的数据不会被改动。
--
-- ============================================================
-- 执行前准备（破坏性操作前置检查，AGENTS.md §安全与破坏性操作）
-- ============================================================
--
-- 1) 备份（必做）：
--      mysqldump -h127.0.0.1 -P3307 -umedconsult -pmedconsult123 \
--        --single-transaction medconsult_ai > backup-medconsult_ai-$(date +%Y%m%d).sql
--
-- 2) dry-run（确认将要新增的列，每条 SELECT 都应返回 0 才会触发 ALTER）：
--      USE medconsult_ai;
--      SELECT 'ai_call_log' AS t, column_name
--        FROM information_schema.columns
--       WHERE table_schema = DATABASE() AND table_name = 'ai_call_log'
--         AND column_name IN
--         ('cache_hit','prompt_tokens','completion_tokens','total_tokens','estimated_cost_yuan');
--      SELECT 'ai_image_detection' AS t, column_name
--        FROM information_schema.columns
--       WHERE table_schema = DATABASE() AND table_name = 'ai_image_detection'
--         AND column_name IN
--         ('submitted_by_user_id','submitted_by_service_code','review_result');
--      SELECT table_name, column_name
--        FROM information_schema.columns
--       WHERE table_schema = DATABASE()
--         AND column_name = 'enabled'
--         AND table_name IN ('symptom_rule','high_risk_symptom_rule','negative_rule');
--
-- 3) 停写窗口（可选但推荐）：
--      本脚本只 ADD COLUMN 不改业务数据，列都有默认值或允许 NULL，
--      理论上可与 ai-service 同时在线；但保守做法是先停 ai-service 写入端再执行。
--
-- ============================================================
-- 执行命令
-- ============================================================
--
--   mysql -h127.0.0.1 -P3307 -umedconsult -pmedconsult123 \
--     medconsult_ai < scripts/review/apply-ai-migrations-20260720.sql
--
-- ============================================================
-- 执行后验证（每条 SELECT 都应返回与下面预期一致的列名）
-- ============================================================
--
--   -- ai_call_log 应返回 5 行
--   SELECT column_name FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'ai_call_log'
--      AND column_name IN
--      ('cache_hit','prompt_tokens','completion_tokens','total_tokens','estimated_cost_yuan');
--
--   -- ai_image_detection 应返回 3 行
--   SELECT column_name FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'ai_image_detection'
--      AND column_name IN
--      ('submitted_by_user_id','submitted_by_service_code','review_result');
--
--   -- 三张规则表 enabled 应返回 3 行
--   SELECT table_name, column_name FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND column_name = 'enabled'
--      AND table_name IN ('symptom_rule','high_risk_symptom_rule','negative_rule');
--
-- ============================================================
-- 回滚（如确实需要，仅对本次新增列）
-- ============================================================
--
--   ALTER TABLE ai_call_log
--     DROP COLUMN estimated_cost_yuan,
--     DROP COLUMN total_tokens,
--     DROP COLUMN completion_tokens,
--     DROP COLUMN prompt_tokens,
--     DROP COLUMN cache_hit;
--
--   ALTER TABLE ai_image_detection
--     DROP COLUMN review_result,
--     DROP COLUMN submitted_by_service_code,
--     DROP COLUMN submitted_by_user_id;
--   DROP INDEX idx_ai_image_submitter_user ON ai_image_detection;
--   DROP INDEX idx_ai_image_submitter_service ON ai_image_detection;
--
--   ALTER TABLE symptom_rule DROP COLUMN enabled;
--   ALTER TABLE high_risk_symptom_rule DROP COLUMN enabled;
--   ALTER TABLE negative_rule DROP COLUMN enabled;
--
-- ============================================================

-- ----------------------------------------------------------------------
-- 1) ai_call_log 可观测性字段（来源：upgrade-ai-call-log-observability-20260718.sql）
-- ----------------------------------------------------------------------

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'cache_hit') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN cache_hit TINYINT NOT NULL DEFAULT 0 AFTER request_id',
    'SELECT ''ai_call_log.cache_hit already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'prompt_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN prompt_tokens INT NOT NULL DEFAULT 0 AFTER cache_hit',
    'SELECT ''ai_call_log.prompt_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'completion_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN completion_tokens INT NOT NULL DEFAULT 0 AFTER prompt_tokens',
    'SELECT ''ai_call_log.completion_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'total_tokens') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN total_tokens INT NOT NULL DEFAULT 0 AFTER completion_tokens',
    'SELECT ''ai_call_log.total_tokens already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_call_log'
        AND column_name = 'estimated_cost_yuan') = 0,
    'ALTER TABLE ai_call_log ADD COLUMN estimated_cost_yuan DECIMAL(12,6) NOT NULL DEFAULT 0.000000 AFTER total_tokens',
    'SELECT ''ai_call_log.estimated_cost_yuan already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 2) ai_image_detection 影像归属与复核字段（来源：upgrade-ai-imaging-security-20260718.sql）
-- ----------------------------------------------------------------------

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND column_name = 'submitted_by_user_id') = 0,
    'ALTER TABLE ai_image_detection ADD COLUMN submitted_by_user_id BIGINT NULL AFTER record_id',
    'SELECT ''ai_image_detection.submitted_by_user_id already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND column_name = 'submitted_by_service_code') = 0,
    'ALTER TABLE ai_image_detection ADD COLUMN submitted_by_service_code VARCHAR(64) NULL AFTER submitted_by_user_id',
    'SELECT ''ai_image_detection.submitted_by_service_code already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND column_name = 'review_result') = 0,
    'ALTER TABLE ai_image_detection ADD COLUMN review_result VARCHAR(32) NULL AFTER reviewed_by',
    'SELECT ''ai_image_detection.review_result already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 同源脚本还包含 ai_report_text_analysis.review_result，本脚本一并幂等补上
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_report_text_analysis'
        AND column_name = 'review_result') = 0,
    'ALTER TABLE ai_report_text_analysis ADD COLUMN review_result VARCHAR(32) NULL AFTER reviewed_by',
    'SELECT ''ai_report_text_analysis.review_result already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 同源脚本补两条 submitter 索引；索引不存在才建（用 information_schema.statistics 判断）
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND index_name = 'idx_ai_image_submitter_user') = 0,
    'CREATE INDEX idx_ai_image_submitter_user ON ai_image_detection (submitted_by_user_id)',
    'SELECT ''idx_ai_image_submitter_user already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'ai_image_detection'
        AND index_name = 'idx_ai_image_submitter_service') = 0,
    'CREATE INDEX idx_ai_image_submitter_service ON ai_image_detection (submitted_by_service_code)',
    'SELECT ''idx_ai_image_submitter_service already exists'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------
-- 3) 三张症状规则表 enabled 字段（来源：upgrade-ai-rule-enabled-20260719.sql）
-- ----------------------------------------------------------------------

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'symptom_rule') > 0
    AND
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'symptom_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE symptom_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER category',
    'SELECT ''symptom_rule.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'high_risk_symptom_rule') > 0
    AND
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'high_risk_symptom_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE high_risk_symptom_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER advice',
    'SELECT ''high_risk_symptom_rule.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'negative_rule') > 0
    AND
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'negative_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE negative_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER match_strategy',
    'SELECT ''negative_rule.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
