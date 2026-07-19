-- AI rule table compatibility migration for existing databases (2026-07-19).
-- New databases already get these columns from schema-ai.sql; this file only
-- fills older tables created before enabled-based rule filtering was added.

SET @sql := IF(
    (SELECT COUNT(*)
       FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'symptom_rule') > 0
    AND
    (SELECT COUNT(*)
       FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'symptom_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE symptom_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER category',
    'SELECT ''symptom_rule.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*)
       FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'high_risk_symptom_rule') > 0
    AND
    (SELECT COUNT(*)
       FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'high_risk_symptom_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE high_risk_symptom_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER advice',
    'SELECT ''high_risk_symptom_rule.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*)
       FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'negative_rule') > 0
    AND
    (SELECT COUNT(*)
       FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'negative_rule'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE negative_rule ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER match_strategy',
    'SELECT ''negative_rule.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
