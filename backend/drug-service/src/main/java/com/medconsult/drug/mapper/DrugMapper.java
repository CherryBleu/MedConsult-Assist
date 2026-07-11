package com.medconsult.drug.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.drug.entity.Drug;
import org.apache.ibatis.annotations.Mapper;

/**
 * drug Mapper。
 */
@Mapper
public interface DrugMapper extends BaseMapper<Drug> {
}
