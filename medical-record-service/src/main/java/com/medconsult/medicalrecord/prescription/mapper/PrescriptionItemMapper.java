package com.medconsult.medicalrecord.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.medicalrecord.prescription.entity.PrescriptionItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处方明细 Mapper（纯 BaseMapper，无自定义 SQL，无 XML）。
 */
@Mapper
public interface PrescriptionItemMapper extends BaseMapper<PrescriptionItem> {
}
