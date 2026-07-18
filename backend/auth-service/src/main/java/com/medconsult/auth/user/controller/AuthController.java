package com.medconsult.auth.user.controller;

import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.auth.user.service.AuthService;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    /**
     * 绑定患者档案到当前登录用户（补建档场景）。
     *
     * <p>用于历史脏账号（sys_user.patient_id 为 NULL，绕过"注册即建档"流程）补全档案关联。
     * 后端自动用 sys_user 已有的 name/phone 建档，前端只需补充 idCard 等缺失字段。
     * 绑定成功后重签 JWT 并返回新 token，前端存储后无需重新登录即可使用挂号/AI问诊等功能。
     */
    @PostMapping("/me/bind-patient")
    @Operation(summary = "绑定患者档案（补建档）")
    public Result<AuthDTO.BindPatientResponse> bindPatient(@Valid @RequestBody AuthDTO.BindPatientRequest req) {
        Long userId = SecurityContext.requireUser().userId();
        return Result.ok(authService.bindPatient(userId, req));
    }

    /**
     * 管理员查询用户列表（用户管理页）。
     *
     * <p>权限：仅 HOSPITAL_ADMIN 可访问（service 层手动校验角色，非管理员抛 FORBIDDEN）。
     * <p>返回分页用户列表（{@link PageResult}），不含密码摘要字段。
     *
     * @param page     页码（从 1 开始，默认 1）
     * @param pageSize 每页条数（默认 20，上限 100）
     * @param keyword  可选：按账号/姓名/手机号模糊搜索
     * @param role     可选：角色过滤（PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN）
     */
    @GetMapping("/users")
    @Operation(summary = "管理员查询用户列表（用户管理页）")
    public Result<PageResult<AuthDTO.UserListItem>> users(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，上限 100") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "账号/姓名/手机号模糊搜索（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "角色过滤（可选）") @RequestParam(required = false) String role) {
        return Result.ok(authService.listUsers(page, pageSize, keyword, role));
    }

    /** 管理员创建账号（含管理类角色 PHARMACY_ADMIN/HOSPITAL_ADMIN，区别于自助 /register） */
    @PostMapping("/users")
    @Operation(summary = "管理员创建账号")
    public Result<AuthDTO.UserInfo> createUser(@Valid @RequestBody AuthDTO.RegisterRequest req) {
        return Result.ok(authService.createUserByAdmin(req));
    }

    /** 管理员删除用户（软删，不能删自己） */
    @DeleteMapping("/users/{userId}")
    @Operation(summary = "管理员删除用户")
    public Result<Void> deleteUser(@Parameter(description = "用户 ID", required = true) @PathVariable Long userId) {
        authService.deleteUser(userId);
        return Result.ok();
    }

    /** §2.1 修改当前登录用户密码（校验原密码 + 频率限制防爆破） */
    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(@Valid @RequestBody AuthDTO.ChangePasswordRequest req) {
        Long userId = com.medconsult.common.security.SecurityContext.requireUser().userId();
        authService.changePassword(userId, req);
        return Result.ok();
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
