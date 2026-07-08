package com.medconsult.drug.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品批次库存表（对应《数据库设计文档》§2.9 drug_stock_batch 表，含《修改建议》§5.1 补 deleted）。
 *
 * <p>每条记录代表某药品某批次的一笔库存。FEFO 出库时按 {@code expireDate} 升序选批扣减。
 * {@code quantity} 为该批次当前剩余数量。
 *
 * <p>状态：
 * <ul>
 *   <li>AVAILABLE：可用（出库可选）</li>
 *   <li>EXPIRED：已过期（入库时若 expire_date &lt; 今日则置 EXPIRED；出库不选）</li>
 *   <li>DISABLED：停用（人工冻结，不参与出库）</li>
 * </ul>
 */
@Getter
@Setter
@TableName("drug_stock_batch")
public class DrugStockBatch extends BaseEntity {

    /** 药品 ID */
    private Long drugId;

    /** 批次号（业务可读，如 BATCH20260701），uk_batch_no 唯一 */
    private String batchNo;

    /** 当前批次剩余数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 生产日期 */
    private LocalDate productionDate;

    /** 有效期（FEFO 排序依据） */
    private LocalDate expireDate;

    /** 供应商 */
    private String supplier;

    /** 状态：AVAILABLE / EXPIRED / DISABLED */
    private String status;
}
