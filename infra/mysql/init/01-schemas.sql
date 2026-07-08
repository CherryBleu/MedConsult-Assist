-- ============================================================
-- MedConsult-Assist MySQL 初始化（架构文档 §1.2 表归属矩阵）
-- 红线：每张表只能归一个服务独占。禁止跨 schema join / 视图 / 触发器。
-- 执行时机：docker-entrypoint-initdb.d，MySQL 容器首次启动时自动跑。
-- ============================================================

-- 创建 7 个业务 schema（按服务划分，端口映射见 docker-compose.yml）
CREATE DATABASE IF NOT EXISTS medconsult_auth       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS medconsult_patient    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS medconsult_outpatient DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS medconsult_record     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS medconsult_drug       DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS medconsult_ai         DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS medconsult_notify     DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 统一应用账号：medconsult（compose 里 MYSQL_USER 注入的账号默认只能连到 INFORMATION_SCHEMA，
-- 这里显式 GRANT，让该账号能访问 7 个业务 schema）
-- 红线：生产环境应收紧到 per-service 独立账号（一服务一账号）；本地冒烟用统一账号足够。
GRANT ALL PRIVILEGES ON medconsult_auth.*       TO 'medconsult'@'%';
GRANT ALL PRIVILEGES ON medconsult_patient.*    TO 'medconsult'@'%';
GRANT ALL PRIVILEGES ON medconsult_outpatient.* TO 'medconsult'@'%';
GRANT ALL PRIVILEGES ON medconsult_record.*     TO 'medconsult'@'%';
GRANT ALL PRIVILEGES ON medconsult_drug.*       TO 'medconsult'@'%';
GRANT ALL PRIVILEGES ON medconsult_ai.*         TO 'medconsult'@'%';
GRANT ALL PRIVILEGES ON medconsult_notify.*     TO 'medconsult'@'%';

FLUSH PRIVILEGES;

-- 备注：具体业务表 DDL 由各服务在自己的 schema.sql 里维护（架构文档 §3.2.3），
--      服务启动时通过 spring.sql.init 自动执行，本脚本只建空 schema + 授权。
