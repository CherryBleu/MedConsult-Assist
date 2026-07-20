-- 增量迁移：AI 反馈增加 rating 字段（1-5 星评分）
-- 旧 useful/adopted 字段保留兼容历史数据，新写入只写 rating
-- 幂等：先判断列是否存在再 ADD
-- 注意：MySQL 8 + 没有 IF NOT EXISTS on ADD COLUMN，需用 stored procedure 或先 DROP 再判断
-- 这里用 information_schema 判断的幂等写法
DROP PROCEDURE IF EXISTS ai_feedback_add_rating_20260720;
DELIMITER //
CREATE PROCEDURE ai_feedback_add_rating_20260720()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_feedback'
          AND column_name = 'rating'
    ) THEN
        ALTER TABLE ai_feedback
            ADD COLUMN rating TINYINT NOT NULL DEFAULT 0 AFTER useful;
    END IF;
END //
DELIMITER ;
CALL ai_feedback_add_rating_20260720();
DROP PROCEDURE IF EXISTS ai_feedback_add_rating_20260720;
