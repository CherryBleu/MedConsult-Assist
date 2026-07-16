package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * RBAC 角色表（架构文档 §3.2）。
 *
 * <p>四种内置角色：PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN。
 * 角色-权限通过 {@link SysRolePermission} 关联，支持一人多角色（{@link SysUserRole}）。
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 角色编码：PATIENT/DOCTOR/PHARMACY_ADMIN/HOSPITAL_ADMIN */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 描述 */
    private String description;

    /** 是否启用：0 否 1 是 */
    private Integer enabled;
}
