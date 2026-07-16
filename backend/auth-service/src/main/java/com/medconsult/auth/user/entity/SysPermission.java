package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * RBAC 权限点表（架构文档 §3.2）。
 *
 * <p>权限编码形如 {@code patient:read}，由 {@link SysRolePermission} 关联到角色，
 * 配合 data_scope 控制数据可见范围（ALL/DEPT/SELF/ASSIGNED）。
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 权限编码，如 patient:read */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 资源类型 */
    private String resourceType;

    /** 操作：read/write/audit/export */
    private String action;

    /** 描述 */
    private String description;
}
