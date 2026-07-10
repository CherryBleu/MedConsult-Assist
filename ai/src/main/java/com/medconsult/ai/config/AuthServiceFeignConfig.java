package com.medconsult.ai.config;

import com.medconsult.common.web.RequestContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class AuthServiceFeignConfig {
    @Bean
    public RequestInterceptor authServiceTraceInterceptor() {
        return template -> template.header(RequestContext.TRACE_ID_HEADER, RequestContext.traceId());
    }
}
