package com.medconsult.auth.serviceaccount.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.auth.serviceaccount.dto.ServiceAccountDTO;
import com.medconsult.auth.serviceaccount.entity.SysServiceAccount;
import com.medconsult.auth.serviceaccount.mapper.SysServiceAccountMapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.security.JwtCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 服务账号服务（架构文档 §4.2）。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>换发服务 token：按 serviceCode 查账号，BCrypt 校验 apiKey，签发 SERVICE 类型 JWT</li>
 *   <li>token 内携带 scope（权限点），下游服务按 scope 细粒度鉴权</li>
 * </ul>
 *
 * <p>服务 token 与用户 token 共用同一 {@link JwtCodec}（HS256 对称签名，§4.2），
 * 下游 {@code SecurityContext.requireService()} 按 {@code subjectType==SERVICE} 判定。
 */
@Service
@RequiredArgsConstructor
public class ServiceAccountService {

    private final SysServiceAccountMapper serviceAccountMapper;
    private final JwtCodec jwtCodec;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /** 服务 token 有效期：1 小时（服务 token 短期，降低泄露风险） */
    @Value("${medconsult.security.jwt.service-ttl:3600}")
    private long serviceTtl;

    /**
     * 用 serviceCode + apiKey 换发服务身份 JWT。
     *
     * @param req 服务编码 + API Key
     * @return 服务 token 响应
     * @throws BusinessException 账号不存在 / 已禁用 / apiKey 错误（统一 UNAUTHORIZED，不泄露具体原因）
     */
    public ServiceAccountDTO.ServiceTokenResponse issueToken(ServiceAccountDTO.ServiceTokenRequest req) {
        SysServiceAccount account = serviceAccountMapper.selectOne(
                new QueryWrapper<SysServiceAccount>().eq("service_code", req.getServiceCode()));
        // 统一返回 UNAUTHORIZED，避免枚举服务账号是否存在（防探测）
        if (account == null || "DISABLED".equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "服务凭证无效");
        }
        if (!passwordEncoder.matches(req.getApiKey(), account.getApiKeyHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "服务凭证无效");
        }
        List<String> scope = parseScope(account.getScope());
        String token = jwtCodec.signService(account.getServiceCode(),
                account.getServiceName() != null ? account.getServiceName() : account.getServiceCode(),
                scope, serviceTtl, null);
        return new ServiceAccountDTO.ServiceTokenResponse(token, "Bearer", serviceTtl, account.getServiceCode());
    }

    private static List<String> parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
