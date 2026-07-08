package com.medconsult.outpatient.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medconsult.outpatient.appointment.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

/**
 * appointment Mapper。
 */
@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
