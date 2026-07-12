package com.medconsult.auth.user.service;

import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.common.core.PageResult;

/**
 * 认证服务接口（对齐《接口文档》§2.1）。
 *
 * <p>方法对应 §2.1.1~§2.1.5 的 5 个对外接口：
 * register / login / refresh / logout / me。
 * 另含管理员用户列表查询（用户管理页）。
 */
public interface AuthService {

    AuthDTO.UserInfo register(AuthDTO.RegisterRequest req);

    AuthDTO.LoginResponse login(AuthDTO.LoginRequest req, String ip, String userAgent);

    AuthDTO.RefreshResponse refresh(AuthDTO.RefreshRequest req);

    boolean logout(AuthDTO.LogoutRequest req);

    /**
     * 当前用户信息。
     * @param userId 当前登录用户 ID（由 Controller 从 SecurityContext 传入）
     */
    AuthDTO.MeResponse me(Long userId);

    /**
     * 管理员查询用户列表（用户管理页）。
     *
     * <p>权限：仅 HOSPITAL_ADMIN 可调用（service 内手动校验，不依赖 @Permission 切面）。
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword  按账号/姓名/手机号模糊搜索（可空）
     * @param role     角色过滤（可空）
     * @return 分页用户列表（不含密码摘要）
     */
    PageResult<AuthDTO.UserListItem> listUsers(int page, int pageSize, String keyword, String role);
}
