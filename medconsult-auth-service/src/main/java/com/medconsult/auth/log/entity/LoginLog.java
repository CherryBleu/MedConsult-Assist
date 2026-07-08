package com.medconsult.auth.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录日志表（《修改建议》§2.2 配套表）。
 *
 * <p>流水表，不继承 BaseEntity（无 updated_at / deleted，只追加）。
 */
@Getter
@Setter
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String account;

    /** 登录角色（便于按角色统计登录行为） */
    private String role;

    /** 登录方式：PASSWORD / REFRESH / SSO */
    private String loginType;

    /** 登录结果：SUCCESS / WRONG_PWD / LOCKED / DISABLED */
    private String loginResult;

    private String ip;

    private String userAgent;

    private String deviceInfo;

    private LocalDateTime loginAt;

    private LocalDateTime logoutAt;
}
