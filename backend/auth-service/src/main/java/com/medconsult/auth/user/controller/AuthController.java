package com.medconsult.auth.user.controller;

import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.auth.user.service.AuthService;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口（对齐《接口文档》§2.1）。
 *
 * <p>路径前缀 /api/v1/auth（对外，走 Gateway）。
 */
@Tag(name = "认证接口", description = "用户注册/登录/刷新/登出/当前用户信息（§2.1）")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** §2.1.1 用户注册 */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<AuthDTO.UserInfo> register(@Valid @RequestBody AuthDTO.RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    /** §2.1.2 用户登录 */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<AuthDTO.LoginResponse> login(@Valid @RequestBody AuthDTO.LoginRequest req,
                                                HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        String ua = httpReq.getHeader("User-Agent");
        return Result.ok(authService.login(req, ip, ua));
    }

    /** §2.1.3 刷新 Token */
    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<AuthDTO.RefreshResponse> refresh(@Valid @RequestBody AuthDTO.RefreshRequest req) {
        return Result.ok(authService.refresh(req));
    }

    /** §2.1.4 退出登录 */
    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Boolean> logout(@Valid @RequestBody AuthDTO.LogoutRequest req) {
        return Result.ok(authService.logout(req));
    }

    /**
     * §2.1.5 当前用户信息。
     * <p>身份来自 {@link SecurityContext}（由 {@code JwtAuthServletFilter} 从网关 X-User-* 头
     * 或原始 Authorization 头解析写入）。不再直接读 Authorization 头——网关已剥离它（§4.4）。
     */
    @GetMapping("/me")
    @Operation(summary = "当前用户信息")
    public Result<AuthDTO.MeResponse> me() {
        Long userId = SecurityContext.requireUser().userId();
        return Result.ok(authService.me(userId));
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
