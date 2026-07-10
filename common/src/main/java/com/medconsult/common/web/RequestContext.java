package com.medconsult.common.web;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class RequestContext {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CALLER_SERVICE_HEADER = "X-Caller-Service";
    public static final String TRIGGER_USER_ID_HEADER = "X-Trigger-User-Id";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String INTERNAL_API_KEY_HEADER = "X-Service-Api-Key";
    public static final String INTERNAL_PRINCIPAL_ATTRIBUTE = "internalServicePrincipal";

    private RequestContext() {
    }

    public static String traceId() {
        String traceId = MDC.get("traceId");
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        traceId = "trace-" + UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        return traceId;
    }
}
