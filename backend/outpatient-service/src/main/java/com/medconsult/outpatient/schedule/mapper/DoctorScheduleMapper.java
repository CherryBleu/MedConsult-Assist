package com.medconsult.outpatient.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * doctor_schedule Mapper。
 */
@Mapper
public interface DoctorScheduleMapper extends BaseMapper<DoctorSchedule> {
}
