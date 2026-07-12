-- ============================================================
-- 脚本: create-admin.sql
-- 用途: 创建医院管理员账号 admin / 123456（角色 HOSPITAL_ADMIN）
-- 数据库: MySQL 8.0，schema = medconsult_auth，表 sys_user
-- 作者: ZCode 生成（基于 auth-service DataSeeder.java + AuthServiceImpl 调查）
-- ============================================================
--
-- 【账号规格】（对齐 auth-service DataSeeder 第 54-56 行的种子账号）
--   account      = admin
--   password     = 123456（明文）
--   password_hash = BCrypt cost=10 摘要（见下方，已预生成）
--   role         = HOSPITAL_ADMIN（医院管理员，与前端 UserManage.vue 第 53 行一致）
--   user_no      = U0000001（与 DataSeeder 保持一致）
--   id           = 1（与 DataSeeder 保持一致；若已被占用请改用 9001）
--   patient_id   = NULL（管理员不建档）
--   doctor_id    = NULL（管理员非医生）
--   status       = ACTIVE
--   phone        = 13800000000（与 DataSeeder 一致；唯一约束，勿重复）
--
-- ★★★ 关键警告：角色不在 sys_user 表里，光跑 SQL 不够！ ★★★
--   sys_user 表【没有 role 列】（见 auth-service/src/main/resources/schema.sql）。
--   角色 HOSPITAL_ADMIN 只能存于 Redis key：
--       medconsult:auth:role:<userId>
--   登录时 AuthServiceImpl.login()（第 211-217 行）从该 key 读 primaryRole，
--   读不到就兜底成 "PATIENT"。所以【只跑本 SQL 不写 Redis，admin 登录后会变成
--   PATIENT 角色】，无法进入管理员后台。
--
--   正确姿势二选一：
--   【方案1（推荐）】别用 SQL，直接清空 sys_user 表后重启 auth-service，
--                   让 DataSeeder（Java）自动种 admin——它会同时写好 Redis 角色。
--                   清表：TRUNCATE TABLE sys_user;（会删全部账号，谨慎）
--   【方案2】跑本 SQL 后，手动用 redis-cli 补角色 key（见脚本末尾【第四部分】）。
--
-- 【BCrypt 哈希来源】
--   密码 123456 的 BCrypt(cost=10) 摘要由 Python 现场生成：
--       python -c "import bcrypt; print(bcrypt.hashpw(b'123456', bcrypt.gensalt(10)).decode())"
--   注意：BCrypt 每次生成的 salt 不同，哈希串不同但都有效（cost 一致即可校验通过）。
--   Spring Security 的 BCryptPasswordEncoder(cost=10) 与 Python bcrypt 的 $2b$/$2a$
--   前缀互通（AuthServiceImpl 第 71 行用 new BCryptPasswordEncoder(10)）。
-- ============================================================

-- 显式指定目标 schema
USE medconsult_auth;

-- ===== 第一部分：创建 admin 账号 =====
-- INSERT IGNORE：account/phone/user_no 均有唯一约束，重复执行不报错（幂等）。
-- password_hash 是 "123456" 的 BCrypt cost=10 摘要（Python bcrypt 生成）。
INSERT IGNORE INTO sys_user (
    id,
    user_no,
    account,
    phone,
    password_hash,
    name,
    patient_id,
    doctor_id,
    status,
    created_at,
    updated_at,
    deleted
) VALUES (
    1,                                  -- 主键（与 DataSeeder 对齐；占用时改 9001）
    'U0000001',                         -- 用户编号（业务可读）
    'admin',                            -- 登录账号
    '13800000000',                      -- 手机号（唯一约束）
    '$2b$10$7XrOCY7jI0rCX48qTHBjNu0HEDLELRXc9/GxVXolSLJNh2wkTPDxy',  -- BCrypt("123456", cost=10)
    '系统管理员',                        -- 姓名
    NULL,                               -- patient_id：管理员不建档
    NULL,                               -- doctor_id：管理员非医生
    'ACTIVE',                           -- 账号状态
    CURRENT_TIMESTAMP(3),
    CURRENT_TIMESTAMP(3),
    0                                   -- 逻辑删除：未删除
);


-- ===== 第二部分：验证 sys_user 写入 =====
SELECT id, user_no, account, phone, name, patient_id, doctor_id, status, deleted
FROM sys_user
WHERE account = 'admin';

-- 密码校验提示：无法用 SQL 直接校验 BCrypt（单向哈希）。
-- 要验证 admin/123456 能登录，请走应用层：POST /auth/login {"account":"admin","password":"123456"}
-- 若返回 role=PATIENT 而非 HOSPITAL_ADMIN，说明 Redis 角色 key 没写——见【第四部分】。


-- ===== 第三部分（必做）：写 Redis 角色 key =====
-- 【SQL 无法操作 Redis】，以下命令请在 redis-cli 中执行（不是 SQL）：
--
--   redis-cli SET medconsult:auth:role:1 HOSPITAL_ADMIN
--   redis-cli EXPIRE medconsult:auth:role:1 31536000
--
-- 说明：
--   - key 格式 = medconsult:auth:role:<userId>，userId = sys_user.id（这里是 1）
--     （来源：AuthServiceImpl.java 第 298-300 行 roleKey() 方法）
--   - value = HOSPITAL_ADMIN（角色码，与前端 UserManage.vue 第 53 行 / DataSeeder 第 56 行一致）
--   - TTL = 31536000 秒（365 天，与 DataSeeder/register 的 Duration.ofDays(365) 对齐）
--
-- 如果 id 改成了 9001，相应改成：
--   redis-cli SET medconsult:auth:role:9001 HOSPITAL_ADMIN
--   redis-cli EXPIRE medconsult:auth:role:9001 31536000
--
-- 验证 Redis 写入：
--   redis-cli GET medconsult:auth:role:1
--   （期望返回：HOSPITAL_ADMIN）


-- ===== 第四部分：端到端验证（应用层） =====
-- 1. 确认 Redis：redis-cli GET medconsult:auth:role:1  →  HOSPITAL_ADMIN
-- 2. 调登录接口（任选其一）：
--    curl -X POST http://localhost:8080/auth/login \
--         -H 'Content-Type: application/json' \
--         -d '{"account":"admin","password":"123456"}'
--    或前端登录页用 admin / 123456 登录。
-- 3. 检查返回的 userInfo.role == "HOSPITAL_ADMIN"，且能访问管理员菜单。
--    若 role == "PATIENT"，回到第三部分检查 Redis key 是否写对（含 userId 是否匹配）。


-- ===== 备注：已有 DataSeeder 时的冲突处理 =====
-- 项目 auth-service 自带 DataSeeder（CommandLineRunner），启动时若 sys_user 为空会自动
-- 种 admin/doctor/patient 三个账号（含 Redis 角色）。所以：
--   - 若库是全新空库：直接重启 auth-service 即可，无需本脚本；
--   - 若库已有数据但缺 admin（或 admin 被误删）：用本脚本补，并务必补 Redis key（第三部分）；
--   - 若想完全重建种子：TRUNCATE sys_user 后重启服务（会重种全部 3 个账号）。
