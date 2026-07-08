package com.medconsult.auth.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medconsult.common.mybatis.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户表（对应《修改建议》§2.3 表 5 调整后的 sys_user）。
 *
 * <p><b>关键调整</b>（vs 原《数据库设计文档》2.1）：
 * <ul>
 *   <li>删除原 {@code role} 字段——移到 sys_user_role 表，支持一人多角色（§2.3 表 4）</li>
 *   <li>保留 patient_id / doctor_id，二者可同时有（医生自己生病也是患者）</li>
 * </ul>
 *
 * <p>密码摘要用 {@code password_hash}，BCrypt cost=10（《修改建议》§5.3）。
 * 身份证/手机等敏感字段不在本表（在 patient 表）。
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 用户编号，如 U202607060001 */
    private String userNo;

    /** 登录账号，唯一 */
    private String account;

    /** 手机号，唯一 */
    private String phone;

    /** 密码加密摘要（BCrypt） */
    private String passwordHash;

    /** 用户姓名 */
    private String name;

    /** 关联患者档案 ID，可为空 */
    private Long patientId;

    /** 关联医生 ID，可为空 */
    private Long doctorId;

    /** 账号状态：ACTIVE / DISABLED / LOCKED */
    private String status;

    /** 最后登录时间 */
    private java.time.LocalDateTime lastLoginAt;
}
