package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-权限关联表（架构文档 §3.2）。
 *
 * <p>多对多：一个角色可挂多个权限，一个权限可被多角色拥有。
 * {@code dataScope} 在此表细粒度控制该角色对该权限的数据范围（覆盖默认）。
 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    /** 角色 ID（关联 sys_role.id） */
    private Long roleId;

    /** 权限点 ID（关联 sys_permission.id） */
    private Long permissionId;

    /** 数据范围：ALL/DEPT/SELF/ASSIGNED */
    private String dataScope;
}
