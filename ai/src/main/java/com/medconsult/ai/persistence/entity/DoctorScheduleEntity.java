package com.medconsult.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("doctor_schedule")
public class DoctorScheduleEntity {
    @TableId
    private Long id;
    private String scheduleNo;
    private Long doctorId;
    private Long departmentId;
    private LocalDate scheduleDate;
    private String period;
    private Integer totalQuota;
    private Integer bookedQuota;
    private BigDecimal registrationFee;
    private String status;

    public Long getId() {
        return id;
    }

    public String getScheduleNo() {
        return scheduleNo;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public String getPeriod() {
        return period;
    }

    public Integer getRemainingQuota() {
        return Math.max(0, (totalQuota == null ? 0 : totalQuota) - (bookedQuota == null ? 0 : bookedQuota));
    }

    public String getStatus() {
        return status;
    }
}
