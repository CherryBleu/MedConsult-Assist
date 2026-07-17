package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色表（对应《修改建议》§2.3 表 1）。
 *
 * <p>固定 4 个角色：PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN。
 * <p><b>AI_SERVICE 不进此表</b>——它是服务身份，走 sys_service_account。
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色编码：PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 描述 */
    private String description;

    /** 是否启用：0 否 1 是 */
    private Integer enabled;
}
