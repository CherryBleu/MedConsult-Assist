package com.medconsult.auth.user.service;

import com.medconsult.auth.user.dto.AuthDTO;

/**
 * 认证服务接口（对齐《接口文档》§2.1）。
 *
 * <p>方法对应 §2.1.1~§2.1.5 的 5 个对外接口：
 * register / login / refresh / logout / me。
 */
public interface AuthService {

    AuthDTO.UserInfo register(AuthDTO.RegisterRequest req);

    AuthDTO.LoginResponse login(AuthDTO.LoginRequest req, String ip, String userAgent);

    AuthDTO.RefreshResponse refresh(AuthDTO.RefreshRequest req);

    boolean logout(AuthDTO.LogoutRequest req);

    AuthDTO.MeResponse me(String accessToken);
}
