package com.medconsult.drug.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.drug.entity.DrugStockFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * drug_stock_flow Mapper（流水表只追加，无逻辑删除，BaseMapper 的 deleteById 不会被业务调用）。
 */
@Mapper
public interface DrugStockFlowMapper extends BaseMapper<DrugStockFlow> {
}
