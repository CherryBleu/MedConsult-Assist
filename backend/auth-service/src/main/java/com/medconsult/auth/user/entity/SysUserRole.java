package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色关联表（架构文档 §3.2，支持一人多角色）。
 *
 * <p>替代 sys_user 旧的单值 role 字段（§2.3 表 4 调整）。
 * {@code isPrimary} 标记用户的主角色（默认 1），用于登录时默认进入的视角。
 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole extends BaseEntity {

    /** 用户 ID（关联 sys_user.id） */
    private Long userId;

    /** 角色 ID（关联 sys_role.id） */
    private Long roleId;

    /** 是否主角色：0 否 1 是 */
    private Integer isPrimary;
}
