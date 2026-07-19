-- auth-service RBAC permission compatibility migration (2026-07-20).
-- New databases get sys_permission.enabled from schema.sql. This script is for
-- older medconsult_auth databases created before disabled-permission filtering.

SET @sql := IF(
    (SELECT COUNT(*)
       FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_permission') > 0
    AND
    (SELECT COUNT(*)
       FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'sys_permission'
        AND column_name = 'enabled') = 0,
    'ALTER TABLE sys_permission ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER description',
    'SELECT ''sys_permission.enabled already compatible'' AS migration_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
