package com.medconsult.outpatient.doctor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 医生表（对应《数据库设计文档》§2.3 doctor 表）。
 *
 * <p>维护医生基础信息，支撑排班、预约、病历创建和分诊结果展示
 * （《需求文档》§4.3.1）。
 *
 * <p>{@code specialties} 存 JSON 数组串（如 {@code ["高血压","心律失常"]}），
 * service 层用 ObjectMapper 序列化/反序列化。
 */
@Getter
@Setter
@TableName("doctor")
public class Doctor extends BaseEntity {

    /** 医生编号，如 D10001（业务可读，对外暴露） */
    private String doctorNo;

    /** 医生姓名 */
    private String name;

    /** 所属科室 ID（BIGINT 主键） */
    private Long departmentId;

    /** 职称（如 主任医师） */
    private String title;

    /** 擅长方向（JSON 数组串，如 ["高血压","心律失常"]） */
    private String specialties;

    /** 医生简介 */
    private String introduction;

    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;
}
