package com.medconsult.outpatient.schedule.template.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 排班模板表（对应 doctor_schedule_template，后端修改.md #16 默认排班）。
 *
 * <p>表达"每周固定出诊规律"——某医生每周 {@code dayOfWeek} 的 {@code period} 时段出诊。
 * 一键 apply 时按模板周几规则批量生成 {@code doctor_schedule}（具体某天）。
 * 与 doctor_schedule 语义正交，独立成表。
 *
 * <p>dayOfWeek：1=周一 ... 7=周日（对齐 java.time.DayOfWeek.getValue()）。
 * period/totalQuota/registrationFee 等字段在 apply 时复制到生成的排班。
 */
@Getter
@Setter
@TableName("doctor_schedule_template")
public class DoctorScheduleTemplate extends BaseEntity {

    /** 模板编号，如 T202607080001（业务可读，对外暴露） */
    private String templateNo;

    /** 医生 ID（BIGINT 主键） */
    private Long doctorId;

    /** 科室 ID（BIGINT 主键） */
    private Long departmentId;

    /** 周几：1=周一 ... 7=周日 */
    private Integer dayOfWeek;

    /** 时段：MORNING / AFTERNOON / EVENING / FULL_DAY */
    private String period;

    /** 开始时间 */
    private LocalTime startTime;

    /** 结束时间 */
    private LocalTime endTime;

    /** 总号源（apply 时复制到生成的排班） */
    private Integer totalQuota;

    /** 挂号费 */
    private BigDecimal registrationFee;

    /** 是否启用：0 否 1 是（apply 只处理 enabled=1 的模板） */
    private Integer enabled;
}
