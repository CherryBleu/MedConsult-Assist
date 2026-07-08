package com.medconsult.outpatient.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 医生排班表（对应《数据库设计文档》§2.5 doctor_schedule 表）。
 *
 * <p>记录医生出诊日期、时段和号源数量，是预约挂号的号源基础
 * （《需求文档》§4.1.2）。
 *
 * <p>号源三字段关系：{@code remaining = total - booked}。
 * 创建时 {@code booked=0}，状态 AVAILABLE；预约成功 {@code booked++}，
 * 当 {@code booked==total} 时状态自动转 FULL（§4.1.2 规则 2/3）。
 */
@Getter
@Setter
@TableName("doctor_schedule")
public class DoctorSchedule extends BaseEntity {

    /** 排班编号，如 S202607080001（业务可读，对外暴露） */
    private String scheduleNo;

    /** 医生 ID（BIGINT 主键） */
    private Long doctorId;

    /** 科室 ID（BIGINT 主键） */
    private Long departmentId;

    /** 出诊日期 */
    private LocalDate scheduleDate;

    /** 时段：MORNING / AFTERNOON / EVENING / FULL_DAY */
    private String period;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 总号源 */
    private Integer totalQuota;

    /** 已预约号源 */
    private Integer bookedQuota;

    /** 挂号费 */
    private BigDecimal registrationFee;

    /** 状态：AVAILABLE / FULL / SUSPENDED / CANCELLED */
    private String status;
}
