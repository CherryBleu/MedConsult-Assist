package com.medconsult.drug.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 药品库存流水表（对应《数据库设计文档》§2.10 drug_stock_flow 表 + 《修改建议》§5.1 补字段）。
 *
 * <p><b>流水表只追加，不更新、不逻辑删除</b>（架构文档 §5.1 / 《修改建议》§5.1），
 * 故本类 <b>不继承 BaseEntity</b>：无 updated_at、无 deleted。只保留 id（雪花 ID）+ createdAt。
 *
 * <p>多批次出库时，每个被扣减的批次各产生一条 OUTBOUND flow，batch_id 指向对应批次。
 *
 * <p>type 取值：INBOUND 入库 / OUTBOUND 出库 / ADJUST 调整。
 * {@code prescriptionId} / {@code prescriptionItemId}（修订项 §5.1）用于处方出库的处方溯源。
 */
@Getter
@Setter
@TableName("drug_stock_flow")
public class DrugStockFlow {

    /**
     * 主键。流水表只用雪花 ID，不走逻辑删除 / 自动 updatedAt。
     * 显式声明 ASSIGN_ID（不继承 BaseEntity，避免漏配）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 流水编号，如 SF1Z2K3（SF + 雪花 ID base36） */
    private String flowNo;

    /** 药品 ID */
    private Long drugId;

    /** 批次 ID（出入库关联的批次；多批次出库时每批次一条） */
    private Long batchId;

    /** 类型：INBOUND / OUTBOUND / ADJUST */
    private String type;

    /** 变动数量（正数，方向由 type 表达） */
    private Integer quantity;

    /** 变动前库存（drug.current_stock） */
    private Integer beforeQuantity;

    /** 变动后库存（drug.current_stock） */
    private Integer afterQuantity;

    /** 关联业务记录 ID（病历等） */
    private Long relatedRecordId;

    /** 关联处方 ID（《修改建议》§5.1 补，处方出库溯源） */
    private Long prescriptionId;

    /** 关联处方明细 ID（《修改建议》§5.1 补） */
    private Long prescriptionItemId;

    /** 操作人 ID */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间（流水表只插入，无更新） */
    private LocalDateTime createdAt;
}
