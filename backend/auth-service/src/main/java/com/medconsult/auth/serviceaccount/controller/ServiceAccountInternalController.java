package com.medconsult.auth.serviceaccount.controller;

import com.medconsult.auth.serviceaccount.dto.ServiceAccountDTO;
import com.medconsult.auth.serviceaccount.service.ServiceAccountService;
import com.medconsult.common.core.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务账号内部接口（架构文档 §4.2，/internal/auth）。
 *
 * <p>路径前缀 /internal/auth（不配 Gateway 路由，仅服务间 Feign 调用）。
 *
 * <p><b>鉴权说明</b>：本接口是服务获取首个 token 的入口（bootstrap），
 * 不要求调用方已持有服务 token（否则陷入鸡生蛋问题）。凭证校验由
 * {@link ServiceAccountService#issueToken} 通过 serviceCode + apiKey 完成。
 *
 * <p>调用方：ai-service（及未来需要服务身份的微服务）的 ServiceTokenProvider。
 */
@Tag(name = "服务账号内部接口", description = "服务 token 换发（供 ai-service 等微服务获取服务身份 JWT）")
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class ServiceAccountInternalController {

    private final ServiceAccountService serviceAccountService;

    /**
     * 服务 token 换发：serviceCode + apiKey → SERVICE 类型 JWT。
     * <p>下游服务用此 token 调用其他服务的 /internal/**（SecurityContext.requireService 校验）。
     */
    @Operation(summary = "服务 token 换发")
    @PostMapping("/service-token")
    public Result<ServiceAccountDTO.ServiceTokenResponse> issueToken(
            @Valid @RequestBody ServiceAccountDTO.ServiceTokenRequest req) {
        return Result.ok(serviceAccountService.issueToken(req));
    }
}
