-- ============================================================
-- auth-service 表结构（medconsult_auth schema）
-- 字段与《修改建议》§2.2/§2.3 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- ============================================================

-- sys_user 用户账号表（《数据库设计文档》§2.1）
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    user_no         VARCHAR(32)                           COMMENT '用户编号（业务可读）',
    account         VARCHAR(64)                           COMMENT '账号（手机号/账号至少填一项）',
    phone           VARCHAR(20)                           COMMENT '手机号',
    password_hash   VARCHAR(255)                          COMMENT 'BCrypt 摘要，不可逆',
    name            VARCHAR(50)                           COMMENT '姓名',
    patient_id      BIGINT                                COMMENT '关联患者编号',
    doctor_id       BIGINT                                COMMENT '关联医生编号',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE/DISABLED/LOCKED',
    last_login_at   DATETIME(3)                           COMMENT '最后登录时间',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_account (account),
    UNIQUE KEY uk_sys_user_phone (phone),
    UNIQUE KEY uk_sys_user_user_no (user_no),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账号表';

-- sys_service_account 服务账号表（架构文档 §4.2，服务间调用的服务身份凭证）
-- 服务（如 ai-service）用 service_code + api_key 向 auth-service 换发 SERVICE 类型 JWT，
-- 下游服务通过 SecurityContext.requireService() 校验服务身份（§2.4 内部接口鉴权）。
CREATE TABLE IF NOT EXISTS sys_service_account (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    service_code    VARCHAR(64)  NOT NULL                 COMMENT '服务编码（如 ai-service，对应 JwtPayload.serviceCode）',
    service_name    VARCHAR(128)                          COMMENT '服务显示名',
    api_key         VARCHAR(128) NOT NULL                 COMMENT 'API Key（服务换 token 的凭证，BCrypt 摘要存储）',
    api_key_hash    VARCHAR(255) NOT NULL                 COMMENT 'API Key 的 BCrypt 摘要',
    scope           VARCHAR(1024)                         COMMENT '权限点列表（逗号分隔，如 patient:read,drug:read）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted         TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_service_account_code (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务账号表';

-- 种子数据：ai-service 服务账号（api_key 明文 = dev-ai-service-api-key，BCrypt cost=10 摘要）
-- 冒烟用固定凭证；生产环境从 KMS/Nacos 注入并轮换。
INSERT IGNORE INTO sys_service_account (id, service_code, service_name, api_key, api_key_hash, scope, status)
VALUES (1001, 'ai-service', 'AI 辅助问诊服务', 'dev-ai-service-api-key',
        '$2a$10$AJrdPShL66fUpfw5MvD0.OWxcW1k7/WjleXjfbArxQ.Y.94LuKaau',
        'patient:read,drug:read,medical-record:read', 'ACTIVE');

-- login_log 登录日志表（《修改建议》§2.2，flow 表不逻辑删除、不 extends BaseEntity）
CREATE TABLE IF NOT EXISTS login_log (
    id              BIGINT       NOT NULL                 COMMENT '主键（雪花 ID）',
    user_id         BIGINT                                COMMENT '用户 ID（失败时可为空）',
    account         VARCHAR(64)                           COMMENT '尝试登录的账号/手机号',
    role            VARCHAR(32)                           COMMENT '角色（PATIENT/DOCTOR/...）',
    login_type      VARCHAR(20)                           COMMENT 'PASSWORD/REFRESH',
    login_result    VARCHAR(20)                           COMMENT 'SUCCESS/FAILURE/LOCKED',
    ip              VARCHAR(50)                           COMMENT '登录 IP',
    user_agent      VARCHAR(255)                          COMMENT 'UA',
    device_info     VARCHAR(255)                          COMMENT '设备信息',
    login_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    logout_at       DATETIME(3)                           COMMENT '登出时间',
    PRIMARY KEY (id),
    KEY idx_login_log_user_id (user_id),
    KEY idx_login_log_login_at (login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
