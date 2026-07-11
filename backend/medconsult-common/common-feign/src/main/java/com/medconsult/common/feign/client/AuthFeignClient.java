package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.ServiceTokenDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * auth-service 的 Feign 客户端（架构文档 §4.2）。
 *
 * <p>供需要服务身份的微服务（如 ai-service）换发 SERVICE 类型 JWT。
 * name = {@code "auth-service"} 对应 Nacos 注册的服务名。
 *
 * <p><b>注意</b>：本客户端调用的是 {@code /internal/auth/service-token}，是服务获取首个 token 的
 * bootstrap 入口，凭证在请求体里（serviceCode + apiKey），因此<b>不</b>依赖
 * {@link com.medconsult.common.feign.AuthRelayInterceptor} 注入服务 token。
 * 故用独立 contextId 隔离，避免全局拦截器在此接口上注入无效头。
 */
@FeignClient(name = "auth-service", contextId = "authFeignClient")
public interface AuthFeignClient {

    /**
     * 服务 token 换发：serviceCode + apiKey → SERVICE 类型 JWT。
     */
    @PostMapping("/internal/auth/service-token")
    Result<ServiceTokenDTO.Response> issueServiceToken(@RequestBody ServiceTokenDTO.Request request);
}
