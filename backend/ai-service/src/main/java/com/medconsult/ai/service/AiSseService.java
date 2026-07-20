package com.medconsult.ai.service;

import com.medconsult.ai.config.AiSseEventBus;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.security.AiHeaders;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AiSseService {
    private static final Logger log = LoggerFactory.getLogger(AiSseService.class);

    private final SummaryService summaryService;
    private final MedicationAnalysisService medicationAnalysisService;
    private final TriageService triageService;
    private final AiSseEventBus sseEventBus;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiSseService(SummaryService summaryService,
                        MedicationAnalysisService medicationAnalysisService,
                        TriageService triageService,
                        AiSseEventBus sseEventBus) {
        this.summaryService = summaryService;
        this.medicationAnalysisService = medicationAnalysisService;
        this.triageService = triageService;
        this.sseEventBus = sseEventBus;
    }

    public SseEmitter streamSummary(MedicalRecordSummaryRequest request) {
        JwtPayload actor = SecurityContext.getPayload();
        return emit((userId, streamId) -> summaryService.summarizeRecordStream(
                request,
                actor,
                token -> sendQuietly(userId, streamId, "delta", Map.of("token", token))
        ));
    }

    public SseEmitter streamInternalSummary(MedicalRecordSummaryRequest request, JwtPayload actor) {
        return emit((userId, streamId) -> summaryService.summarizeInternalRecordStream(
                request,
                actor,
                token -> sendQuietly(userId, streamId, "delta", Map.of("token", token))
        ));
    }

    public SseEmitter streamMedication(MedicationAnalysisRequest request) {
        return emit((userId, streamId) -> medicationAnalysisService.analyzeStream(
                request,
                token -> sendQuietly(userId, streamId, "delta", Map.of("token", token))
        ));
    }

    public SseEmitter streamTriage(TriageRequest request) {
        return emit((userId, streamId) -> {
            TriageResponse response = triageService.triage(request);
            response.recommendations().forEach(item -> sendQuietly(userId, streamId, "delta", item));
            return response;
        });
    }

    private SseEmitter emit(Task task) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String userId = currentUserId();
        String streamId = "sse-" + UUID.randomUUID().toString().replace("-", "");
        sseEventBus.register(userId, streamId, emitter);
        // 主线程捕获请求属性 + 身份，在异步线程传播，让 SecurityContext.getPayload()
        // 在 triageService/medicationAnalysisService 等业务方法内可用——它们调用
        // SecurityContext.requireUser() 读 RequestContextHolder（默认 ThreadLocal
        // 不传播到线程池）。不传播则抛 "需要用户登录"，SSE 收到 error 事件。
        org.springframework.web.context.request.RequestAttributes requestAttrs =
                RequestContextHolder.getRequestAttributes();
        JwtPayload actor = SecurityContext.getPayload();
        executor.submit(() -> {
            try {
                if (requestAttrs != null) {
                    RequestContextHolder.setRequestAttributes(requestAttrs, true);
                }
                if (actor != null) {
                    SecurityContext.setPayload(actor);
                }
                publish(userId, streamId, "start", Map.of("status", "PROCESSING"));
                Object result = task.run(userId, streamId);
                publish(userId, streamId, "result", result);
                publish(userId, streamId, "done", Map.of("status", "COMPLETED"));
            } catch (Exception ex) {
                publish(userId, streamId, "error", Map.of("status", "FAILED", "message", ex.getMessage() == null ? "" : ex.getMessage()));
            } finally {
                // 清理异步线程的请求属性，避免线程池泄漏
                RequestContextHolder.resetRequestAttributes();
            }
        });
        return emitter;
    }

    private void sendQuietly(String userId, String streamId, String event, Object data) {
        publish(userId, streamId, event, data);
    }

    private void publish(String userId, String streamId, String event, Object data) {
        sseEventBus.publish(userId, streamId, event, data);
    }

    /**
     * 当前用户 ID：优先读 X-User-Id，其次 X-Trigger-User-Id，兜底用 traceId。
     * （替代 ai-stack RequestContext.traceId()，改读 MDC + 本地 UUID 兜底）
     */
    private static String currentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String userId = attributes == null ? null : attributes.getRequest().getHeader(AiHeaders.USER_ID);
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        userId = attributes == null ? null : attributes.getRequest().getHeader(AiHeaders.TRIGGER_USER_ID);
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        // 兜底：traceId 或随机 ID（保证 channel 唯一性，SSE 不丢流）
        String traceId = MDC.get("traceId");
        return StringUtils.hasText(traceId) ? traceId : "sse-" + UUID.randomUUID();
    }

    @FunctionalInterface
    private interface Task {
        Object run(String userId, String streamId);
    }
}
