package com.medconsult.medicalrecord.medicalrecord.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.medicalrecord.medicalrecord.entity.MedicalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 电子病历 Mapper（纯 BaseMapper，无自定义 SQL，无 XML）。
 *
 * <p>所有查询走 QueryWrapper + 内置方法（selectPage/selectList/selectOne/selectCount/selectBatchIds）。
 */
@Mapper
public interface MedicalRecordMapper extends BaseMapper<MedicalRecord> {
}
