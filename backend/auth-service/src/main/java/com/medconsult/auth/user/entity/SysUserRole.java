package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色关联表（对应《修改建议》§2.3 表 4）。
 *
 * <p>支持一人多角色（医生自己生病也是患者、药房管理员兼任医院管理员）。
 * {@code is_primary} 标记主角色，用于 JWT 的 primaryRole claim。
 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 角色 ID（sys_role.id） */
    private Long roleId;

    /** 是否主角色：0 否 1 是 */
    private Integer isPrimary;
}
