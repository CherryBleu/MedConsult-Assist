package com.medconsult.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.patient.entity.Patient;
import org.apache.ibatis.annotations.Mapper;

/**
 * patient Mapper。
 */
@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
}
