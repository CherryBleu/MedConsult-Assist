package com.medconsult.auth.serviceaccount.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.serviceaccount.dto.ServiceAccountDTO;
import com.medconsult.auth.serviceaccount.entity.SysServiceAccount;
import com.medconsult.auth.serviceaccount.mapper.SysServiceAccountMapper;
import com.medconsult.auth.user.entity.SysUser;
import com.medconsult.auth.user.mapper.SysUserMapper;
import com.medconsult.auth.user.service.RbacQueryService;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtCodec;
import com.medconsult.common.security.JwtPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 架构文档声明的 /internal/auth token 校验与角色查询能力。
 */
@Service
@RequiredArgsConstructor
public class InternalAuthService {

    private final JwtCodec jwtCodec;
    private final SysServiceAccountMapper serviceAccountMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redis;
    private final RbacQueryService rbacQueryService;

    public ServiceAccountDTO.UserTokenVerifyResponse verifyUserToken(String token) {
        JwtPayload payload = jwtCodec.parse(token);
        if (!payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户 Token");
        }
        SysUser user = userMapper.selectById(payload.userId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户 Token 已失效");
        }
        return new ServiceAccountDTO.UserTokenVerifyResponse(
                payload.userId(),
                safeList(payload.roles()),
                payload.primaryRole(),
                safeList(payload.scope()),
                payload.exp());
    }

    public ServiceAccountDTO.ServiceTokenVerifyResponse verifyServiceToken(String token) {
        JwtPayload payload = jwtCodec.parse(token);
        return verifyServicePayload(payload);
    }

    public ServiceAccountDTO.ServiceTokenVerifyResponse verifyServicePayload(JwtPayload payload) {
        if (payload == null || !payload.isService()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要服务 Token");
        }
        SysServiceAccount account = serviceAccountMapper.selectOne(
                new QueryWrapper<SysServiceAccount>().eq("service_code", payload.serviceCode()));
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "服务身份已失效");
        }

        List<String> grantedScope = parseScope(account.getScope());
        List<String> tokenScope = safeList(payload.scope());
        if (!grantedScope.contains("*") && !grantedScope.containsAll(tokenScope)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "服务 Token scope 超出账号授权");
        }
        return new ServiceAccountDTO.ServiceTokenVerifyResponse(
                payload.serviceCode(), tokenScope, payload.exp());
    }

    public ServiceAccountDTO.UserRolesResponse userRoles(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return rbacQueryService.findUserAccess(userId)
                .map(access -> new ServiceAccountDTO.UserRolesResponse(access.roles(), access.primaryRole()))
                .orElseGet(() -> {
                    String primaryRole = resolveRole(userId);
                    return new ServiceAccountDTO.UserRolesResponse(List.of(primaryRole), primaryRole);
                });
    }

    private String resolveRole(Long userId) {
        try {
            String stored = redis.opsForValue().get(roleKey(userId));
            if (stored != null && !stored.isBlank()) {
                return stored;
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用时保持与登录/用户列表相同的 PATIENT 兜底。
        }
        return "PATIENT";
    }

    private static List<String> parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String roleKey(Long userId) {
        return "medconsult:auth:role:" + userId;
    }
}
