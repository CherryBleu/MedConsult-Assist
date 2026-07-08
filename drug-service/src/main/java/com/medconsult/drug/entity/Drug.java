package com.medconsult.drug.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品基础信息表（对应《数据库设计文档》§2.8 drug 表，含《修改建议》§5.1 补充字段）。
 *
 * <p>保存药品的通用信息与用药风险摘要（禁忌 / 相互作用）。
 * {@code contraindications} / {@code interactions} 字段在 DB 为 JSON，
 * 实体用 String 存原始 JSON 串，由 Service 层用 Jackson 解析（避免 MyBatis-Plus 对 JSON 类型
 * 的繁琐 TypeHandler 配置，与 outpatient 的 specialties 处理方式一致）。
 *
 * <p>{@code currentStock} 为冗余字段（修订项 §5.1），等于各可用批次 quantity 汇总，
 * 入库/出库事务内同步更新，便于快速查询（§2.7.2 列表的 stockQuantity）。
 *
 * <p>状态：ACTIVE（正常）/ DISABLED（停用，不可入库，仍可查询）。
 */
@Getter
@Setter
@TableName("drug")
public class Drug extends BaseEntity {

    /** 药品编号，如 D1Z2K3（D + 雪花 ID base36，业务可读，对外暴露） */
    private String drugNo;

    /** 通用名，如 硝苯地平 */
    private String genericName;

    /** 商品名，如 拜新同 */
    private String tradeName;

    /** 规格，如 30mg*7片 */
    private String specification;

    /** 剂型，如 控释片 */
    private String dosageForm;

    /** 生产厂家 */
    private String manufacturer;

    /** 批准文号（国药准字） */
    private String approvalNo;

    /** 单位，如 盒/瓶/支 */
    private String unit;

    /** 最低库存阈值（低于产生 LOW_STOCK 预警） */
    private Integer minStockThreshold;

    /** 禁忌信息（JSON 串：[{condition,level,note}]，Service 层解析） */
    private String contraindications;

    /** 相互作用信息（JSON 串：[{drugCode,effect,level}]，Service 层解析） */
    private String interactions;

    /** 当前总库存（修订项 §5.1：冗余自各批次 quantity 汇总，事务内同步） */
    private Integer currentStock;

    /** 状态：ACTIVE / DISABLED */
    private String status;
}
