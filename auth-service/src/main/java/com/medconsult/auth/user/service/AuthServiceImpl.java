package com.medconsult.auth.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.log.entity.LoginLog;
import com.medconsult.auth.log.mapper.LoginLogMapper;
import com.medconsult.auth.user.dto.AuthDTO;
import com.medconsult.auth.user.entity.SysUser;
import com.medconsult.auth.user.mapper.SysUserMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtCodec;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.common.web.MaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>注册：BCrypt 摘要密码，账号/手机唯一性校验</li>
 *   <li>登录：校验密码 + 状态，签发 access + refresh（refresh 额外存 Redis 便于登出失效）</li>
 *   <li>刷新：校验 refresh 有效性，重签 access</li>
 *   <li>登出：refresh 的 jti 入 Redis 黑名单</li>
 *   <li>当前用户：解析 access，回显（手机号脱敏）</li>
 * </ul>
 *
 * <p>登出态/refresh 黑名单放 Redis 共享，多实例一致（§4.1）。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final JwtCodec jwtCodec;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    /** access token 有效期：2 小时 */
    @Value("${medconsult.security.jwt.access-ttl:7200}")
    private long accessTtl;

    /** refresh token 有效期：7 天 */
    @Value("${medconsult.security.jwt.refresh-ttl:604800}")
    private long refreshTtl;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    // ===== 注册 =====

    @Override
    @Transactional
    public AuthDTO.UserInfo register(AuthDTO.RegisterRequest req) {
        // 唯一性校验：account 必填（DTO @NotBlank 保证），phone 选填但若有则需唯一。
        // 注：需求 §4.1.0 规则 1 原文"账号/手机号至少填一项"，但 login 也按 account 查，
        //     所以 account 为必填——这条更严格，不冲突。
        if (userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("account", req.getAccount())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号已存在: " + req.getAccount());
        }
        if (req.getPhone() != null && userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("phone", req.getPhone())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "手机号已注册");
        }

        SysUser u = new SysUser();
        u.setUserNo(generateUserNo());
        u.setAccount(req.getAccount());
        u.setPhone(req.getPhone());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setName(req.getName());
        u.setPatientId(parseLong(req.getPatientId()));
        u.setDoctorId(parseLong(req.getDoctorId()));
        u.setStatus("ACTIVE");
        userMapper.insert(u);

        return new AuthDTO.UserInfo(
                u.getUserNo(), u.getName(), req.getRole(),
                req.getPatientId(), req.getDoctorId(), u.getStatus());
    }

    // ===== 登录 =====

    @Override
    @Transactional
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest req, String ip, String userAgent) {
        SysUser u = userMapper.selectOne(new QueryWrapper<SysUser>().eq("account", req.getAccount()));
        String failReason = null;

        if (u == null) {
            failReason = "WRONG_PWD";
            writeLoginLog(null, req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if ("DISABLED".equals(u.getStatus())) {
            failReason = "DISABLED";
            writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        if ("LOCKED".equals(u.getStatus())) {
            failReason = "LOCKED";
            writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已锁定");
        }
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
            failReason = "WRONG_PWD";
            writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", failReason);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 登录成功：更新最后登录时间
        u.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(u);

        // 签发双 token。冒烟期角色暂从注册时存（实际 RBAC 五表阶段查 sys_user_role）
        String primaryRole = "PATIENT"; // 默认；后续从 sys_user_role 查
        List<String> roles = List.of(primaryRole);
        List<String> scope = List.of("*"); // 冒烟期全权限；RBAC 阶段改为查 sys_role_permission

        String access = jwtCodec.signUser(u.getId(), u.getName(), roles, primaryRole,
                u.getPatientId(), u.getDoctorId(), scope, accessTtl, null);
        String refresh = jwtCodec.signUser(u.getId(), u.getName(), roles, primaryRole,
                u.getPatientId(), u.getDoctorId(), scope, refreshTtl, null);

        // refresh 入 Redis（key=refresh:{userId}:{jti}）便于登出/校验
        JwtPayload refreshPayload = jwtCodec.parse(refresh);
        redis.opsForValue().set(refreshKey(refreshPayload.jti()), "1",
                Duration.ofSeconds(refreshTtl));

        writeLoginLog(u.getId(), req.getAccount(), ip, userAgent, "PASSWORD", "SUCCESS");

        return new AuthDTO.LoginResponse(
                access, refresh, "Bearer", accessTtl,
                new AuthDTO.UserInfo(u.getUserNo(), u.getName(), primaryRole,
                        u.getPatientId() != null ? String.valueOf(u.getPatientId()) : null,
                        u.getDoctorId() != null ? String.valueOf(u.getDoctorId()) : null,
                        u.getStatus()));
    }

    // ===== 刷新 =====

    @Override
    public AuthDTO.RefreshResponse refresh(AuthDTO.RefreshRequest req) {
        JwtPayload p = jwtCodec.parse(req.getRefreshToken());
        if (!isRefreshValid(p.jti())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refreshToken 已失效（已登出或过期）");
        }
        // 重签 access
        String access = jwtCodec.signUser(p.userId(), p.name(), p.roles(), p.primaryRole(),
                p.patientId(), p.doctorId(), p.scope(), accessTtl, null);
        return new AuthDTO.RefreshResponse(access, "Bearer", accessTtl);
    }

    // ===== 登出 =====

    @Override
    public boolean logout(AuthDTO.LogoutRequest req) {
        JwtPayload p = jwtCodec.parse(req.getRefreshToken());
        // refresh jti 入黑名单（删除 Redis 中的有效标记）
        redis.delete(refreshKey(p.jti()));
        return true;
    }

    // ===== 当前用户 =====

    @Override
    public AuthDTO.MeResponse me(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 角色取自 SecurityContext（网关已解析）；如无则回退到默认 PATIENT（兼容直连场景）
        JwtPayload p = SecurityContext.getPayload();
        String primaryRole = (p != null && p.primaryRole() != null) ? p.primaryRole() : "PATIENT";
        return new AuthDTO.MeResponse(
                u.getUserNo(),
                u.getAccount(),
                u.getName(),
                MaskType.PHONE.mask(u.getPhone()),
                primaryRole,
                u.getPatientId() != null ? String.valueOf(u.getPatientId()) : null,
                u.getDoctorId() != null ? String.valueOf(u.getDoctorId()) : null,
                u.getStatus());
    }

    // ===== 私有助手 =====

    private boolean isRefreshValid(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(refreshKey(jti)));
    }

    private static String refreshKey(String jti) {
        return "medconsult:auth:refresh:" + jti;
    }

    private void writeLoginLog(Long userId, String account, String ip, String userAgent,
                               String loginType, String result) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setAccount(account);
        log.setLoginType(loginType);
        log.setLoginResult(result);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(log);
    }

    /**
     * 生成用户编号：U + 雪花序列（由 MyBatis-Plus IdWorker 生成的 Long 的无符号 hex）。
     * <p>替代旧的 currentTimeMillis + random*1000 方案——后者并发碰撞概率高（同毫秒仅 1000 桶），
     * 雪花 ID 单调递增且分布式唯一，碰撞可忽略；DB 仍有 uk_sys_user_user_no 兜底。
     */
    private static String generateUserNo() {
        long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        return "U" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
