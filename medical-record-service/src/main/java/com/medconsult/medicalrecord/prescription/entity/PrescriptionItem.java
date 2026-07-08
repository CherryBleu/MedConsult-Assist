package com.medconsult.medicalrecord.prescription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 处方明细表（对应《修改建议》§2.1 表 2 prescription_item）。
 *
 * <p>每条记录对应处方里的一味药（药品 + 用法用量 + 价格）。
 *
 * <p><b>drug_id 本批可空</b>：第 1 批不调 drug-service，开方时只接受 drugName + specification 文本快照，
 * drug_id 列本批存 null。第 2 批 dispense 调剂发药时，通过 drug_no 反查 drug-service 解析回填，
 * 无需改表结构。
 *
 * <p><b>allocated_batch_id / dispensed_quantity 本批可空</b>：调剂发药（第 2 批）FEFO 选批后回填。
 */
@Getter
@Setter
@TableName("prescription_item")
public class PrescriptionItem extends BaseEntity {

    /** 处方 ID（BIGINT 主键，关联 prescription.id） */
    private Long prescriptionId;

    /** 药品 ID（BIGINT，本批可空；第 2 批 dispense 时回填） */
    private Long drugId;

    /** 药品名快照（防药品库变更影响历史处方，《修改建议》§2.1） */
    private String drugNameSnapshot;

    /** 规格快照 */
    private String specificationSnapshot;

    /** 单次剂量（如 "30mg"） */
    private String dosage;

    /** 频次（如 "每日一次"） */
    private String frequency;

    /** 给药途径（如 "口服"，可空） */
    private String route;

    /** 天数 */
    private Integer days;

    /** 总数量（DECIMAL，按天数 × 频次计算或医生直接填） */
    private BigDecimal quantity;

    /** 单位（如 "片"、"盒"） */
    private String unit;

    /** 单价（DECIMAL，本批由前端传或留 null；第 2 批可从 drug 批次查） */
    private BigDecimal unitPrice;

    /** 小计 = quantity × unitPrice */
    private BigDecimal subtotal;

    /** 调剂后锁定的批次 ID（FEFO 选批后回填，第 2 批；本批 null） */
    private Long allocatedBatchId;

    /** 已发数量（调剂发药后回填，第 2 批；本批 null） */
    private BigDecimal dispensedQuantity;
}
