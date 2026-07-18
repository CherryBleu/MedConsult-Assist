package com.medconsult.common.mq.audit;

import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Builds audit events for annotated successful write methods and stores them in
 * the local outbox through {@link AuditLogProducer}.
 */
@Aspect
public class AuditLogAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private final AuditLogProducer producer;

    public AuditLogAspect(AuditLogProducer producer) {
        this.producer = producer;
    }

    @Around("@annotation(auditLog) || @within(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        AuditLog effective = resolveEffective(pjp, auditLog);
        Object result = pjp.proceed();
        producer.enqueue(toEvent(effective, pjp, result));
        return result;
    }

    private AuditLogEvent toEvent(AuditLog auditLog, ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext context = contextFor(pjp, result);
        AuditLogEvent event = new AuditLogEvent();
        event.setTraceId(resolveTraceId());
        event.setResourceType(auditLog.resourceType());
        event.setAction(auditLog.action());
        event.setResourceId(stringValue(auditLog.resourceId(), context));
        event.setResourceName(stringValue(auditLog.resourceName(), context));
        event.setTargetOwnerId(longValue(auditLog.targetOwnerId(), context));
        event.setDetail(stringValue(auditLog.detail(), context));
        event.setResult("SUCCESS");

        JwtPayload payload = SecurityContext.getPayload();
        if (payload != null) {
            if (payload.isUser()) {
                event.setOperatorId(payload.userId() == null ? null : String.valueOf(payload.userId()));
                event.setOperatorRole(payload.primaryRole());
            } else if (payload.isService()) {
                event.setOperatorId(payload.serviceCode());
                event.setOperatorRole("SERVICE");
            }
            event.setOperatorName(payload.name());
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            event.setIp(clientIp(request));
            event.setUserAgent(request.getHeader("User-Agent"));
        }
        return event;
    }

    private StandardEvaluationContext contextFor(ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Method method = methodOf(pjp);
        Object[] args = pjp.getArgs();
        context.setVariable("args", args);
        context.setVariable("result", result);
        context.setVariable("methodName", method.getName());
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }
        String[] parameterNames = PARAMETER_NAMES.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return context;
    }

    private static String stringValue(String expression, StandardEvaluationContext context) {
        Object value = evaluate(expression, context);
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(String expression, StandardEvaluationContext context) {
        Object value = evaluate(expression, context);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : Long.valueOf(text);
    }

    private static Object evaluate(String expression, StandardEvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return PARSER.parseExpression(expression).getValue(context);
    }

    private static AuditLog resolveEffective(ProceedingJoinPoint pjp, AuditLog candidate) {
        Method method = methodOf(pjp);
        AuditLog methodAnnotation = AnnotationUtils.findAnnotation(method, AuditLog.class);
        return methodAnnotation != null ? methodAnnotation : candidate;
    }

    private static Method methodOf(ProceedingJoinPoint pjp) {
        if (pjp.getSignature() instanceof MethodSignature signature) {
            return signature.getMethod();
        }
        throw new IllegalStateException("@AuditLog only supports method join points");
    }

    private static String resolveTraceId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        Object attr = request.getAttribute(TraceIdFilter.REQUEST_ATTR_KEY);
        if (attr instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String header = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        return header == null || header.isBlank() ? null : header;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
