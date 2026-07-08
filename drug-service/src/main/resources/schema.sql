-- ============================================================
-- drug-service 表结构（medconsult_drug schema）
-- 字段与《数据库设计文档》§2.8 drug / §2.9 drug_stock_batch / §2.10 drug_stock_flow 对齐；MySQL 8.0 语法
-- 执行时机：spring.sql.init.mode=always，服务启动时执行
-- 修订项（落实《修改建议》§5.1）：
--   drug            补 current_stock / deleted；contraindications/interactions 用 JSON
--   drug_stock_batch补 deleted
--   drug_stock_flow 补 prescription_id / prescription_item_id（流水表只追加，无 updated_at/deleted）
-- ============================================================

-- drug 药品表（《数据库设计文档》§2.8 / 《需求文档》§4.1.5）
CREATE TABLE IF NOT EXISTS drug (
    id                 BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    drug_no            VARCHAR(32)   NOT NULL                 COMMENT '药品编号（D + 雪花 ID base36）',
    generic_name       VARCHAR(100)  NOT NULL                 COMMENT '通用名',
    trade_name         VARCHAR(100)                           COMMENT '商品名',
    specification      VARCHAR(100)                           COMMENT '规格（如 30mg*7片）',
    dosage_form        VARCHAR(50)                            COMMENT '剂型（如 控释片）',
    manufacturer       VARCHAR(200)                           COMMENT '生产厂家',
    approval_no        VARCHAR(100)                           COMMENT '批准文号（国药准字）',
    unit               VARCHAR(20)                            COMMENT '单位（盒/瓶/支）',
    min_stock_threshold INT          NOT NULL DEFAULT 0       COMMENT '最低库存阈值（低于产生 LOW_STOCK 预警）',
    -- 修订项 §5.1：contraindications/interactions 改 JSON 结构化（《修改建议》§4.4），便于校验和查询
    contraindications  JSON                                   COMMENT '禁忌信息（JSON 数组：[{condition,level,note}]）',
    interactions       JSON                                   COMMENT '相互作用信息（JSON 数组：[{drugCode,effect,level}]）',
    -- 修订项 §5.1：补 current_stock（接口字段 stockQuantity 缺列）
    current_stock      INT           NOT NULL DEFAULT 0       COMMENT '当前总库存（冗余自各批次 quantity 汇总，便于快速查询）',
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    created_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    -- 修订项 §5.1：所有业务实体表统一补 deleted
    deleted            TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drug_no (drug_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品基础信息表';

-- drug_stock_batch 药品批次库存表（《数据库设计文档》§2.9 / 《需求文档》§4.1.5）
CREATE TABLE IF NOT EXISTS drug_stock_batch (
    id              BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    drug_id         BIGINT        NOT NULL                 COMMENT '药品 ID',
    batch_no        VARCHAR(64)   NOT NULL                 COMMENT '批次号（业务可读）',
    quantity        INT           NOT NULL DEFAULT 0       COMMENT '当前批次剩余数量',
    unit_price      DECIMAL(10,2)                          COMMENT '单价',
    production_date DATE                                   COMMENT '生产日期',
    expire_date     DATE          NOT NULL                 COMMENT '有效期（FEFO 排序依据）',
    supplier        VARCHAR(200)                           COMMENT '供应商',
    status          VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE/EXPIRED/DISABLED',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    -- 修订项 §5.1：补 deleted
    deleted         TINYINT       NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 否 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    -- FEFO 查询核心索引：按 drug_id 过滤 + 按 expire_date 升序排（近效期优先）
    KEY idx_batch_drug_expire (drug_id, expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品批次库存表';

-- drug_stock_flow 药品库存流水表（《数据库设计文档》§2.10 / 《修改建议》§5.1 补字段）
-- 流水表只追加，不逻辑删除，不更新（故无 updated_at / deleted）
CREATE TABLE IF NOT EXISTS drug_stock_flow (
    id                  BIGINT        NOT NULL                 COMMENT '主键（雪花 ID）',
    flow_no             VARCHAR(32)   NOT NULL                 COMMENT '流水编号（SF + 雪花 ID base36）',
    drug_id             BIGINT        NOT NULL                 COMMENT '药品 ID',
    batch_id            BIGINT                                 COMMENT '批次 ID（出入库关联的批次；多批次出库时每批次一条）',
    type                VARCHAR(20)   NOT NULL                 COMMENT '类型：INBOUND 入库/OUTBOUND 出库/ADJUST 调整',
    quantity            INT           NOT NULL                 COMMENT '变动数量（正数）',
    before_quantity     INT                                    COMMENT '变动前库存（drug.current_stock）',
    after_quantity      INT                                    COMMENT '变动后库存（drug.current_stock）',
    related_record_id   BIGINT                                 COMMENT '关联业务记录 ID（病历等）',
    -- 修订项 §5.1：补 prescription_id / prescription_item_id（处方出库溯源）
    prescription_id     BIGINT                                 COMMENT '关联处方 ID',
    prescription_item_id BIGINT                                COMMENT '关联处方明细 ID',
    operator_id         BIGINT                                 COMMENT '操作人 ID',
    remark              VARCHAR(500)                           COMMENT '备注',
    created_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_no (flow_no),
    KEY idx_flow_drug (drug_id),
    KEY idx_flow_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品库存流水表（只追加）';
