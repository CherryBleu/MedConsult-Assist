-- AI 调用日志可观测性增量字段（2026-07-18）。
-- 适用于已经执行过 upgrade-ai-architecture-20260710.sql 的环境；新建库直接使用 schema-ai.sql。

ALTER TABLE ai_call_log
    ADD COLUMN cache_hit TINYINT NOT NULL DEFAULT 0 AFTER request_id,
    ADD COLUMN prompt_tokens INT NOT NULL DEFAULT 0 AFTER cache_hit,
    ADD COLUMN completion_tokens INT NOT NULL DEFAULT 0 AFTER prompt_tokens,
    ADD COLUMN total_tokens INT NOT NULL DEFAULT 0 AFTER completion_tokens,
    ADD COLUMN estimated_cost_yuan DECIMAL(12,6) NOT NULL DEFAULT 0.000000 AFTER total_tokens;
