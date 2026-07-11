package com.medconsult.outpatient.department.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室表（对应《数据库设计文档》§2.2 department 表）。
 *
 * <p>维护医院科室基础信息，支撑医生管理、排班和智能分诊推荐
 * （《需求文档》§4.3.1）。本服务对科室只读查询，创建/维护接口暂未实现。
 */
@Getter
@Setter
@TableName("department")
public class Department extends BaseEntity {

    /** 科室编号，如 DEP_CARDIOLOGY（业务可读，对外暴露） */
    private String departmentNo;

    /** 科室名称 */
    private String name;

    /** 科室介绍 */
    private String description;

    /** 科室位置 */
    private String location;

    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;
}
