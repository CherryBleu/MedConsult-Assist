-- AI 文件上传归属与授权凭据修复（2026-07-18）。
-- 适用于已存在 ai_file_upload 的环境；新建库直接使用 schema-ai.sql。

ALTER TABLE ai_file_upload
    ADD COLUMN uploaded_by_user_id BIGINT NULL AFTER patient_id,
    ADD COLUMN uploaded_by_service_code VARCHAR(64) NULL AFTER uploaded_by_user_id,
    ADD INDEX idx_ai_file_uploader_user (uploaded_by_user_id),
    ADD INDEX idx_ai_file_uploader_service (uploaded_by_service_code);

-- file_url 仅保留不可直接授权访问的对象定位符；下载 URL 由服务按请求动态签发。
UPDATE ai_file_upload
SET file_url = CONCAT('minio://', bucket, '/', object_key)
WHERE storage_type = 'MINIO';
