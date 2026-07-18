-- Add the imaging review and submitter ownership fields to an existing AI database.
-- Run once after upgrade-ai-architecture-20260710.sql.
--
-- Safe rollout preconditions:
-- 1. Stop new imaging submissions. If the query below returns a non-zero value,
--    allow only the existing image workers to drain the queue and repeat the query.
-- 2. Continue only after the result is zero, then stop the image workers and all
--    remaining imaging/report review writes for the migration window:
--      SELECT COUNT(*) AS in_flight_count
--      FROM ai_image_detection
--      WHERE status IN ('PENDING', 'PROCESSING');
-- 3. Back up both ai_image_detection and ai_report_text_analysis, and verify that
--    the backup can be read, before executing the statements below.
--
-- This migration is additive and intentionally performs no ownership backfill.
-- Historical rows whose submitter fields remain NULL are readable only by their
-- patient owner and HOSPITAL_ADMIN; doctors and services cannot read those rows.

ALTER TABLE ai_report_text_analysis
    ADD COLUMN review_result VARCHAR(32) NULL AFTER reviewed_by;

ALTER TABLE ai_image_detection
    ADD COLUMN submitted_by_user_id BIGINT NULL AFTER record_id,
    ADD COLUMN submitted_by_service_code VARCHAR(64) NULL AFTER submitted_by_user_id,
    ADD COLUMN review_result VARCHAR(32) NULL AFTER reviewed_by;

CREATE INDEX idx_ai_image_submitter_user
    ON ai_image_detection (submitted_by_user_id);

CREATE INDEX idx_ai_image_submitter_service
    ON ai_image_detection (submitted_by_service_code);

-- Post-migration verification (run manually; the first two queries should return
-- four rows and two indexes respectively):
--   SELECT table_name, column_name, column_type, is_nullable
--   FROM information_schema.columns
--   WHERE table_schema = DATABASE()
--     AND ((table_name = 'ai_report_text_analysis' AND column_name = 'review_result')
--       OR (table_name = 'ai_image_detection' AND column_name IN
--           ('submitted_by_user_id', 'submitted_by_service_code', 'review_result')))
--   ORDER BY table_name, ordinal_position;
--
--   SELECT table_name, index_name, column_name
--   FROM information_schema.statistics
--   WHERE table_schema = DATABASE()
--     AND table_name = 'ai_image_detection'
--     AND index_name IN ('idx_ai_image_submitter_user', 'idx_ai_image_submitter_service')
--   ORDER BY index_name, seq_in_index;
--
--   SELECT COUNT(*) AS null_submitter_count
--   FROM ai_image_detection
--   WHERE submitted_by_user_id IS NULL AND submitted_by_service_code IS NULL;
--
--   SELECT COUNT(*) AS in_flight_count
--   FROM ai_image_detection
--   WHERE status IN ('PENDING', 'PROCESSING');
