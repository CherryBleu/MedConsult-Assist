package com.medconsult.auth.user.controller;

import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.auth.user.service.AuthService;
import com.medconsult.common.core.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（对齐《接口文档》§2.1）。
 *
 * <p>路径前缀 /api/v1/auth（对外，走 Gateway）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** §2.1.1 用户注册 */
    @PostMapping("/register")
    public Result<AuthDTO.UserInfo> register(@Valid @RequestBody AuthDTO.RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    /** §2.1.2 用户登录 */
    @PostMapping("/login")
    public Result<AuthDTO.LoginResponse> login(@Valid @RequestBody AuthDTO.LoginRequest req,
                                                HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        String ua = httpReq.getHeader("User-Agent");
        return Result.ok(authService.login(req, ip, ua));
    }

    /** §2.1.3 刷新 Token */
    @PostMapping("/refresh")
    public Result<AuthDTO.RefreshResponse> refresh(@Valid @RequestBody AuthDTO.RefreshRequest req) {
        return Result.ok(authService.refresh(req));
    }

    /** §2.1.4 退出登录 */
    @PostMapping("/logout")
    public Result<Boolean> logout(@Valid @RequestBody AuthDTO.LogoutRequest req) {
        return Result.ok(authService.logout(req));
    }

    /** §2.1.5 当前用户信息（从 Authorization 头取 access token） */
    @GetMapping("/me")
    public Result<AuthDTO.MeResponse> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        return Result.ok(authService.me(token));
    }

    private static String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        return ip != null ? ip : req.getRemoteAddr();
    }
}
