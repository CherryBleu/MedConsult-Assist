package com.medconsult.ai.config;

import com.medconsult.common.web.RequestContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public class FeignInternalConfig {
    @Bean
    public RequestInterceptor internalServiceHeaderInterceptor(AiProperties properties) {
        return template -> {
            if (properties.internal() != null) {
                template.header(RequestContext.CALLER_SERVICE_HEADER, properties.internal().serviceCode());
                if (StringUtils.hasText(properties.internal().serviceToken())) {
                    template.header("Authorization", "Bearer " + properties.internal().serviceToken());
                } else {
                    template.header(RequestContext.INTERNAL_API_KEY_HEADER, properties.internal().apiKey());
                }
            }
            template.header(RequestContext.TRACE_ID_HEADER, RequestContext.traceId());
        };
    }
}
