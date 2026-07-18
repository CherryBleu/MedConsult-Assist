package com.medconsult.ai.service;

import com.medconsult.ai.config.AiSseEventBus;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryRequest;
import com.medconsult.ai.dto.AiModels.MedicalRecordSummaryResponse;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisRequest;
import com.medconsult.ai.dto.AiModels.MedicationAnalysisResponse;
import com.medconsult.ai.dto.AiModels.PrescriptionDto;
import com.medconsult.ai.dto.AiModels.TriageRecommendationDto;
import com.medconsult.ai.dto.AiModels.TriageRequest;
import com.medconsult.ai.dto.AiModels.TriageResponse;
import com.medconsult.ai.security.AiHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSseServiceTest {

    private SummaryService summaryService;
    private MedicationAnalysisService medicationAnalysisService;
    private TriageService triageService;
    private AiSseEventBus eventBus;
    private AiSseService service;
    private List<PublishedEvent> publishedEvents;
    private AtomicReference<Registration> registration;
    private CountDownLatch terminalEvent;

    @BeforeEach
    void setUp() {
        summaryService = mock(SummaryService.class);
        medicationAnalysisService = mock(MedicationAnalysisService.class);
        triageService = mock(TriageService.class);
        eventBus = mock(AiSseEventBus.class);
        publishedEvents = new CopyOnWriteArrayList<>();
        registration = new AtomicReference<>();
        terminalEvent = new CountDownLatch(1);

        doAnswer(invocation -> {
            registration.set(new Registration(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)
            ));
            return null;
        }).when(eventBus).register(anyString(), anyString(), any(SseEmitter.class));
        doAnswer(invocation -> {
            PublishedEvent event = new PublishedEvent(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3)
            );
            publishedEvents.add(event);
            if ("done".equals(event.name()) || "error".equals(event.name())) {
                terminalEvent.countDown();
            }
            return null;
        }).when(eventBus).publish(anyString(), anyString(), anyString(), any());

        service = new AiSseService(summaryService, medicationAnalysisService, triageService, eventBus);
    }

    @AfterEach
    void clearContexts() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void summaryStreamShouldPublishTokensResultAndCompletionForHeaderUser() throws Exception {
        bindHeaders("user-17", "trigger-ignored");
        MedicalRecordSummaryRequest request = new MedicalRecordSummaryRequest("MR-1", "STRUCTURED", false);
        MedicalRecordSummaryResponse response = new MedicalRecordSummaryResponse(
                "SUM-1", "MR-1", Map.of("diagnosis", "上呼吸道感染"), "GENERATED"
        );
        when(summaryService.summarizeRecordStream(any(), any())).thenAnswer(invocation -> {
            assertSame(request, invocation.getArgument(0));
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("第一段");
            consumer.accept("第二段");
            return response;
        });

        SseEmitter emitter = service.streamSummary(request);
        awaitTerminalEvent();

        Registration registered = registration.get();
        assertEquals("user-17", registered.userId());
        assertSame(emitter, registered.emitter());
        assertEquals(120_000L, emitter.getTimeout());
        assertTrue(registered.streamId().matches("sse-[0-9a-f]{32}"));
        assertEventSequence("start", "delta", "delta", "result", "done");
        assertEquals(Map.of("status", "PROCESSING"), publishedEvents.get(0).data());
        assertEquals(Map.of("token", "第一段"), publishedEvents.get(1).data());
        assertEquals(Map.of("token", "第二段"), publishedEvents.get(2).data());
        assertSame(response, publishedEvents.get(3).data());
        assertEquals(Map.of("status", "COMPLETED"), publishedEvents.get(4).data());
        assertAllEventsUseRegistration(registered);
    }

    @Test
    void medicationStreamShouldUseTriggerHeaderWhenUserHeaderIsBlank() throws Exception {
        bindHeaders("  ", "doctor-29");
        MedicationAnalysisRequest request = medicationRequest();
        MedicationAnalysisResponse response = new MedicationAnalysisResponse(
                "MA-1", "LOW", List.of(), List.of(), List.of(Map.of("message", "按医嘱用药")), null
        );
        when(medicationAnalysisService.analyzeStream(any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("用药分析片段");
            return response;
        });

        service.streamMedication(request);
        awaitTerminalEvent();

        assertEquals("doctor-29", registration.get().userId());
        assertEventSequence("start", "delta", "result", "done");
        assertEquals(Map.of("token", "用药分析片段"), publishedEvents.get(1).data());
        assertSame(response, publishedEvents.get(2).data());
    }

    @Test
    void triageStreamShouldPublishEachRecommendationAndUseTraceIdFallback() throws Exception {
        MDC.put("traceId", "trace-sse-42");
        TriageRequest request = new TriageRequest(
                null, List.of("胸痛"), "30分钟", "HIGH", 56, "MALE", List.of(), false, null
        );
        TriageRecommendationDto emergency = new TriageRecommendationDto(
                "DEP_EMERGENCY", "急诊科", 0.99, 1, "胸痛需立即评估", List.of()
        );
        TriageRecommendationDto cardiology = new TriageRecommendationDto(
                "DEP_CARDIOLOGY", "心内科", 0.88, 2, "排查心血管疾病", List.of()
        );
        TriageResponse response = new TriageResponse(true, List.of(emergency, cardiology));
        when(triageService.triage(request)).thenReturn(response);

        service.streamTriage(request);
        awaitTerminalEvent();

        assertEquals("trace-sse-42", registration.get().userId());
        assertEventSequence("start", "delta", "delta", "result", "done");
        assertSame(emergency, publishedEvents.get(1).data());
        assertSame(cardiology, publishedEvents.get(2).data());
        assertSame(response, publishedEvents.get(3).data());
    }

    @Test
    void streamShouldPublishFailedEventWithoutDoneWhenTaskThrows() throws Exception {
        IllegalStateException failure = new IllegalStateException("summary unavailable");
        when(summaryService.summarizeRecordStream(any(), any())).thenThrow(failure);

        service.streamSummary(new MedicalRecordSummaryRequest("MR-2", null, false));
        awaitTerminalEvent();

        assertTrue(registration.get().userId().matches("sse-[0-9a-f-]{36}"));
        assertEventSequence("start", "error");
        assertEquals(Map.of("status", "FAILED", "message", "summary unavailable"),
                publishedEvents.get(1).data());
    }

    @Test
    void streamShouldPublishEmptyErrorMessageWhenExceptionMessageIsNull() throws Exception {
        bindHeaders("patient-5", null);
        when(medicationAnalysisService.analyzeStream(any(), any())).thenThrow(new IllegalStateException());

        service.streamMedication(medicationRequest());
        awaitTerminalEvent();

        assertEventSequence("start", "error");
        assertEquals(Map.of("status", "FAILED", "message", ""), publishedEvents.get(1).data());
    }

    private void awaitTerminalEvent() throws InterruptedException {
        assertTrue(terminalEvent.await(3, TimeUnit.SECONDS), "SSE task did not publish a terminal event");
    }

    private void assertEventSequence(String... expected) {
        assertEquals(List.of(expected), publishedEvents.stream().map(PublishedEvent::name).toList());
    }

    private void assertAllEventsUseRegistration(Registration registered) {
        assertTrue(publishedEvents.stream().allMatch(event -> event.userId().equals(registered.userId())));
        assertTrue(publishedEvents.stream().allMatch(event -> event.streamId().equals(registered.streamId())));
    }

    private static MedicationAnalysisRequest medicationRequest() {
        return new MedicationAnalysisRequest(
                "100", "MR-1", "RX-1",
                List.of(new PrescriptionDto("DRUG-1", "阿莫西林", "0.5g", "每日三次", "口服", 5)),
                null,
                false
        );
    }

    private static void bindHeaders(String userId, String triggerUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(AiHeaders.USER_ID, userId);
        }
        if (triggerUserId != null) {
            request.addHeader(AiHeaders.TRIGGER_USER_ID, triggerUserId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private record Registration(String userId, String streamId, SseEmitter emitter) {
    }

    private record PublishedEvent(String userId, String streamId, String name, Object data) {
    }
}
