package com.medconsult.ai.client.internal;

import com.medconsult.ai.config.AuthServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "medconsult-auth-service", configuration = AuthServiceFeignConfig.class)
public interface AuthInternalClient {
    @GetMapping("/internal/auth/service-verify")
    ServiceVerifyResponse verifyService(@RequestHeader("Authorization") String authorization);

    record ServiceVerifyResponse(
            String serviceCode,
            List<String> scope,
            Long exp
    ) {
    }
}
