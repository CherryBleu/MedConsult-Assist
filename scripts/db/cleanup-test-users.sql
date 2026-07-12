-- ============================================================
-- 脚本: cleanup-test-users.sql
-- 用途: 清理"早期未走注册即建档流程"的 PATIENT 测试账号脏数据
--       （sys_user.patient_id 为 NULL 的 PATIENT 账号——这类账号无法预约挂号）
-- 数据库: MySQL 8.0，schema = medconsult_auth
-- 表: sys_user（主表）、login_log（按 user_id 引用）
-- 作者: ZCode 生成（基于 backend/auth-service DataSeeder + AuthServiceImpl 调查）
-- ============================================================
--
-- 【背景】
--   register 流程在 AuthServiceImpl.register() 中：PATIENT 角色注册时必须先调
--   patient-service 建档拿到 BIGINT patientId，再 insert sys_user（patient_id 非空）。
--   早期测试若绕过注册接口直接 INSERT sys_user，会产生 patient_id IS NULL 的
--   PATIENT 账号。这类账号在预约挂号时（AppointmentTxService 按 patient_no 解析）
--   无法关联到患者档案，导致挂号失败，属于脏数据，需清理。
--
--   admin/doctor 等非 PATIENT 角色账号本身 patient_id 就为 NULL（正常），不应被清理。
--   本脚本通过"角色 + patient_id IS NULL"组合判断，仅针对 PATIENT 类脏账号。
--
-- 【重要：角色不在 sys_user 表里】
--   sys_user 表【没有 role 列】（见 auth-service/src/main/resources/schema.sql）。
--   角色存于 Redis key `medconsult:auth:role:{userId}`（TTL 365 天），登录时由
--   AuthServiceImpl.login() 第 211-217 行读取。因此本脚本无法用 SQL 直接判断"角色
--   是否为 PATIENT"——只能用 account/phone 精确匹配（策略A）或 patient_id IS NULL
--   的宽匹配（策略B，较激进，需人工复核）。
--
-- 【用法】
--   1. 先执行本文件【第一部分：SELECT 预览】，确认会删除哪些账号；
--   2. 根据预览结果，把要删的账号填入【策略A】的 IN 列表；
--   3. 注释掉不用的策略，保留要用的；
--   4. 执行【DELETE 部分】；
--   5. 执行【收尾：清理 Redis 角色缓存】（见脚本末尾，需 redis-cli）。
--
-- 【Safe UPDATE 保护】
--   开启 sql_safe_updates，禁止无 WHERE/无 LIMIT 的 DELETE，防止误删全表。
--   策略B 的宽匹配 DELETE 显式带 LIMIT 兜底。
-- ============================================================

-- ===== 全局安全开关：禁止全表 DELETE/UPDATE =====
SET sql_safe_updates = 1;
SET sql_log_bin = 0;  -- 不记 binlog（测试库可关；生产请保留并评估）

-- 显式指定目标 schema（避免在错误数据库执行）
USE medconsult_auth;


-- ############################################################
-- # 第一部分：SELECT 预览（先看会删什么，再决定删不删）        #
-- ############################################################

-- 1.1 查看所有 patient_id 为 NULL 的账号（含 admin/doctor 正常账号，人工甄别）
SELECT
    id,
    user_no,
    account,
    phone,
    name,
    patient_id,
    doctor_id,
    status,
    created_at
FROM sys_user
WHERE patient_id IS NULL
  AND deleted = 0
ORDER BY created_at;

-- 1.2 按账号精确预览（策略A 目标集合，请把这里的列表与策略A 保持一致）
SELECT id, user_no, account, phone, name, patient_id, doctor_id, status, created_at
FROM sys_user
WHERE account IN ('12345678')            -- <-- 在这里增删要清理的早期测试账号
   OR phone IN ('12345678');             -- <-- 或按手机号匹配（早期测试常用 12345678 这种假号）

-- 1.3 查看这些账号的登录日志（login_log 按 user_id 关联，DELETE 时需先清）
SELECT ll.id AS log_id, ll.user_id, ll.account, ll.role, ll.login_result, ll.login_at
FROM login_log ll
WHERE ll.user_id IN (
    SELECT id FROM sys_user
    WHERE account IN ('12345678')
       OR phone IN ('12345678')
);

-- 1.4 查看这些账号是否在 appointment 表留有预约（跨库查询，按需启用）
--     注意：medconsult_outpatient.appointment.patient_id 是 patient 大表主键 BIGINT，
--     而 sys_user.patient_id 可能也为该值；脏账号 patient_id 为 NULL，通常无关联预约。
--     如需复核，取消下面注释并确认连接了对应 schema：
-- -- SELECT a.appointment_no, a.patient_id, a.patient_no, a.status
-- -- FROM medconsult_outpatient.appointment a
-- -- WHERE a.patient_id IN (SELECT patient_id FROM medconsult_auth.sys_user
-- --                         WHERE account IN ('12345678') AND patient_id IS NOT NULL);


-- ############################################################
-- # 第二部分：DELETE（任选策略A 或 策略B，二选一）             #
-- ############################################################

-- ------------------------------------------------------------
-- 【策略A】（推荐）：精确删除指定的早期测试账号
--   适用：已知具体账号/手机号的脏数据（如 12345678 这类假号）。
--   优点：安全，不会误伤 admin/doctor；缺点：要手工列清单。
--   务必先在 1.2/1.3 预览确认！
-- ------------------------------------------------------------
-- 先清 login_log（外键依赖 sys_user.id，虽无物理外键约束，仍按依赖顺序先清）
DELETE FROM login_log
WHERE user_id IN (
    SELECT id FROM (
        SELECT id FROM sys_user
        WHERE account IN ('12345678')        -- <-- 与预览 1.2 保持一致，可增删
           OR phone IN ('12345678')
    ) AS t
);

-- 再删 sys_user 主体（逻辑删除字段 deleted=0 的才删；物理 DELETE 用下面那条）
-- 软删除（推荐，可恢复）：
-- UPDATE sys_user SET deleted = 1
-- WHERE account IN ('12345678') OR phone IN ('12345678');

-- 物理删除（不可恢复，确认无误后启用）：
DELETE FROM sys_user
WHERE account IN ('12345678')               -- <-- 与上面列表保持一致
   OR phone IN ('12345678');


-- ------------------------------------------------------------
-- 【策略B】（激进，默认注释掉）：删除所有 patient_id IS NULL 且非
--          admin/doctor 命名的账号（按命名兜底，无法判 role）
--   风险：sys_user 无 role 列，无法用 SQL 精确排除 HOSPITAL_ADMIN/DOCTOR，
--         只能靠 account/姓名启发式判断。务必先跑 1.1 预览，逐条人工确认！
--   适用：大批量早期脏账号、账号命名有规律（如 test*、数字账号）的场景。
-- ------------------------------------------------------------
-- 先清 login_log
-- DELETE FROM login_log
-- WHERE user_id IN (
--     SELECT id FROM (
--         SELECT id FROM sys_user
--         WHERE patient_id IS NULL
--           AND doctor_id IS NULL
--           AND account NOT IN ('admin')          -- 保护 admin
--           AND account NOT LIKE 'doctor%'        -- 保护 doctor 系（命名兜底）
--           AND deleted = 0
--     ) AS t
-- );
--
-- 再删 sys_user（带 LIMIT 兜底，避免误删全表）
-- DELETE FROM sys_user
-- WHERE patient_id IS NULL
--   AND doctor_id IS NULL
--   AND account NOT IN ('admin')
--   AND account NOT LIKE 'doctor%'
--   AND deleted = 0
-- LIMIT 1000;


-- ############################################################
-- # 第三部分：收尾——清理 Redis 角色缓存（删账号后必做）       #
-- ############################################################
-- 删除 sys_user 行不会自动清掉 Redis 里的角色 key（medconsult:auth:role:{userId}）。
-- 虽不清也不影响（key 有 365 天 TTL 会自然过期），但为避免缓存残留/审计混淆，建议清。
-- SQL 无法操作 Redis，请在 redis-cli 中执行（把 <userId> 换成 1.1 查出的 id）：
--
--   redis-cli DEL medconsult:auth:role:<userId>
--
-- 批量清理多个（示例，用 DEL 一次传多个 key）：
--   redis-cli DEL medconsult:auth:role:100 medconsult:auth:role:101
--
-- 或用 SCAN 找出所有角色 key 再删（谨慎）：
--   redis-cli --scan --pattern 'medconsult:auth:role:*' | xargs -r redis-cli DEL


-- ############################################################
-- # 验证：删除后确认结果                                       #
-- ############################################################
-- 确认目标账号已不存在
SELECT id, account, phone, name FROM sys_user
WHERE account IN ('12345678') OR phone IN ('12345678');

-- 确认 login_log 已无残留
SELECT COUNT(*) AS remaining_logs FROM login_log
WHERE account IN ('12345678');

-- 还原安全开关
SET sql_safe_updates = 1;
