package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限点表（对应《修改建议》§2.3 表 2）。
 *
 * <p>权限编码如 {@code patient:read}、{@code prescription:review}、{@code drug:write}。
 * 本轮建表留空，等接口陆续加 @Permission(code=...) 时再补种子。
 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /** 权限编码，如 patient:read、prescription:review */
    private String permissionCode;

    /** 权限名称 */
    private String permissionName;

    /** 资源类型 */
    private String resourceType;

    /** 操作：read / write / audit / export */
    private String action;

    /** 描述 */
    private String description;
}
