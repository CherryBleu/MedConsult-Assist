-- H2 MySQL 兼容模式 schema，供 MybatisPlusFlowTest 验证自动填充 + 逻辑删除。
-- 列名与 BaseEntity 字段的下划线映射一致：created_at / updated_at / deleted。
CREATE TABLE IF NOT EXISTS test_entity (
    id          BIGINT       NOT NULL,
    name        VARCHAR(100),
    status      VARCHAR(20),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    deleted     INT          DEFAULT 0,
    PRIMARY KEY (id)
);
