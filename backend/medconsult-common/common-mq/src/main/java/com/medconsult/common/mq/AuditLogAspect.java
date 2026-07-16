package com.medconsult.common.mq;

import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * {@link AuditLog} 注解切面（架构文档 §7.5 / 《修改建议》§2.2）。
 *
 * <p><b>事务边界设计（重要）</b>：本切面切 Controller {@code @Around}，在 {@code pjp.proceed()}
 * <b>成功返回后</b>才调 {@link AuditLogProducer#publish} 写 local_message。
 * <ul>
 *   <li>语义："业务成功才审计"。proceed 抛异常（业务失败）→ 不审计。</li>
 *   <li>为何不与业务同 @Transactional：@Transactional 在 Service 层，Controller proceed 返回时
 *       Service 事务已提交，切面已脱离业务事务。要同事务须侵入每个 Service 显式调 producer，成本高。</li>
 *   <li>残余风险：insert local_message 后、Dispatcher 投递前进程崩溃，该审计丢失（窗口极小，一行 insert）。
 *       可接受——审计是辅助链路，不阻断主业务优先。强一致方案留 TODO。</li>
 * </ul>
 *
 * <p><b>上下文来源</b>：
 * <ul>
 *   <li>operatorId/operatorRole/operatorName：{@link SecurityContext} 当前 JwtPayload</li>
 *   <li>traceId：{@link MDC}（TraceIdFilter 注入）</li>
 *   <li>ip/userAgent：{@link HttpServletRequest}（X-Forwarded-For / X-Real-IP / User-Agent）</li>
 *   <li>resourceId：按 {@link ResourceIdFrom} 从路径变量或返回值取</li>
 * </ul>
 *
 * <p><b>容错</b>：审计任何环节异常都<b>不</b>影响已成功的主业务——producer 内部已吞异常，
 * 切面本身也不向外抛审计相关异常。
 */
@Aspect
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    /** 路径变量按此顺序查找 resourceId（除非注解显式指定 pathVarName） */
    private static final String[] DEFAULT_PATH_VAR_NAMES = {"recordId", "prescriptionId"};
    /** 返回值 data 字段按此顺序反射查找 resourceId */
    private static final String[] RESULT_FIELD_NAMES = {"recordId", "prescriptionId"};

    @Autowired
    private AuditLogProducer auditLogProducer;

    /** 供 AutoConfiguration 手动 new 切面时注入 producer（new 出来的对象不走 Spring @Autowired） */
    public void setAuditLogProducer(AuditLogProducer auditLogProducer) {
        this.auditLogProducer = auditLogProducer;
    }

    @Around("@annotation(audit) || @within(audit)")
    public Object around(ProceedingJoinPoint pjp, AuditLog audit) throws Throwable {
        Object result = pjp.proceed();
        // 业务成功才审计；try-catch 确保审计异常不污染已成功的主业务返回值
        try {
            publishAudit(pjp, audit, result);
        } catch (Throwable t) {
            log.warn("审计切面异常（已忽略，不影响主业务）: {}", t.toString());
        }
        return result;
    }

    private void publishAudit(ProceedingJoinPoint pjp, AuditLog audit, Object result) {
        AuditLogEvent event = new AuditLogEvent();
        event.setResourceType(audit.resourceType());
        event.setAction(audit.action());
        event.setResourceName(audit.resourceName());
        event.setResult("SUCCESS");

        // 操作人上下文（未登录场景如匿名接口，留空不报错）
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

        // traceId（MDC，TraceIdFilter 注入）
        event.setTraceId(MDC.get("traceId"));

        // ip / userAgent
        HttpServletRequest req = currentRequest();
        if (req != null) {
            event.setIp(clientIp(req));
            event.setUserAgent(req.getHeader("User-Agent"));
        }

        // resourceId
        event.setResourceId(resolveResourceId(pjp, audit, result, req));

        auditLogProducer.publish(event);
    }

    /** 按 resourceIdFrom 策略取资源编号 */
    private String resolveResourceId(ProceedingJoinPoint pjp, AuditLog audit, Object result, HttpServletRequest req) {
        switch (audit.resourceIdFrom()) {
            case PATH:
                return fromPath(audit, req);
            case RESULT:
                return fromResult(result);
            case NONE:
            default:
                return null;
        }
    }

    /** 从路径变量取（HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE） */
    @SuppressWarnings("unchecked")
    private String fromPath(AuditLog audit, HttpServletRequest req) {
        if (req == null) return null;
        Object attr = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attr instanceof Map<?, ?> vars)) return null;
        // 显式指定的变量名优先
        if (!audit.pathVarName().isBlank()) {
            Object v = vars.get(audit.pathVarName());
            return v == null ? null : String.valueOf(v);
        }
        for (String name : DEFAULT_PATH_VAR_NAMES) {
            Object v = vars.get(name);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }

    /** 从返回值 Result.data 反射取字段（recordId/prescriptionId） */
    private String fromResult(Object result) {
        Object data = extractResultData(result);
        if (data == null) return null;
        for (String fieldName : RESULT_FIELD_NAMES) {
            Object v = readField(data, fieldName);
            if (v != null) return String.valueOf(v);
        }
        return null;
    }

    /** Result<T>.data() 或裸对象 */
    private Object extractResultData(Object result) {
        if (result == null) return null;
        // 反射调 data()，避免 common-mq 直接依赖 common-core 的 Result（虽同仓，反射更解耦）
        Object data = readMethod(result, "data");
        return data != null ? data : result;
    }

    private Object readField(Object obj, String fieldName) {
        Class<?> c = obj.getClass();
        // record 的访问器优先（无参方法），再退回字段
        Object viaAccessor = readMethod(obj, fieldName);
        if (viaAccessor != null) return viaAccessor;
        try {
            Field f = findField(c, fieldName);
            if (f != null) {
                f.setAccessible(true);
                return f.get(obj);
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private Object readMethod(Object obj, String methodName) {
        try {
            return obj.getClass().getMethod(methodName).invoke(obj);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Field findField(Class<?> c, String name) {
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    /** 解析客户端真实 IP（网关在前用 X-Forwarded-For / X-Real-IP，对齐 AuthController.clientIp） */
    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // X-Forwarded-For 可能是 "client, proxy1, proxy2"，取第一个
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        return (ip != null && !ip.isBlank()) ? ip : req.getRemoteAddr();
    }
}
