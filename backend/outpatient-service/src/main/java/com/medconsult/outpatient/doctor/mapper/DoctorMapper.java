package com.medconsult.outpatient.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.outpatient.doctor.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;

/**
 * doctor Mapper。
 */
@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {
}
