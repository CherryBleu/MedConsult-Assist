package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-权限关联表（对应《修改建议》§2.3 表 3）。
 *
 * <p>{@code dataScope} 数据范围：ALL / DEPT / SELF / ASSIGNED。
 * 本轮建表留结构（暂无数据）；行级数据过滤（PermissionAspect 的 SQL 改写）单独评估，不在本批。
 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission extends BaseEntity {

    /** 角色 ID（sys_role.id） */
    private Long roleId;

    /** 权限点 ID（sys_permission.id） */
    private Long permissionId;

    /**
     * 数据范围：ALL 全部 / DEPT 本科室 / SELF 本人 / ASSIGNED 接诊的。
     * 本轮留结构，行级过滤单独评估。
     */
    private String dataScope;
}
