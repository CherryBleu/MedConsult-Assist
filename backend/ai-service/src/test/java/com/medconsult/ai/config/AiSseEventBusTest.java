package com.medconsult.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medconsult.common.redis.SseEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSseEventBusTest {

    private SseEventBus redisBus;
    private ObjectMapper objectMapper;
    private AiSseEventBus eventBus;

    @BeforeEach
    void setUp() {
        redisBus = mock(SseEventBus.class);
        objectMapper = new ObjectMapper();
        eventBus = new AiSseEventBus(redisBus, objectMapper);
    }

    @Test
    void completionShouldRemoveEmitterAndIgnoreLaterEvents() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        emitter.triggerCompletion();
        subscriber.accept(payload("delta", Map.of("token", "late")));

        assertTrue(emitter.sentEvents().isEmpty());
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
    }

    @Test
    void timeoutShouldRemoveEmitterAndIgnoreLaterEvents() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        emitter.triggerTimeout();
        subscriber.accept(payload("delta", Map.of("token", "late")));

        assertTrue(emitter.sentEvents().isEmpty());
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
    }

    @Test
    void clientErrorShouldRemoveEmitterAndIgnoreLaterEvents() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        emitter.triggerError(new IOException("client disconnected"));
        subscriber.accept(payload("delta", Map.of("token", "late")));

        assertTrue(emitter.sentEvents().isEmpty());
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
    }

    @Test
    void cleanupShouldPreventDeliveryThatWasAlreadyParsing() throws Exception {
        BlockingReadObjectMapper blockingMapper = new BlockingReadObjectMapper();
        eventBus = new AiSseEventBus(redisBus, blockingMapper);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);
        String event = payload("delta", Map.of("token", "late"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> delivery = executor.submit(() -> subscriber.accept(event));
            try {
                assertTrue(blockingMapper.awaitReadStarted(), "subscriber did not start parsing");
                emitter.triggerCompletion();
            } finally {
                blockingMapper.allowReadToFinish();
            }
            delivery.get(3, TimeUnit.SECONDS);
        }

        assertTrue(emitter.sentEvents().isEmpty(), "cleanup returned before the captured emitter stopped sending");
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
    }

    @Test
    void subscriberShouldSendDeclaredEventNameAndJsonData() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        subscriber.accept(payload("delta", Map.of("token", "片段")));

        assertSentEvent(emitter, "delta", Map.of("token", "片段"));
        assertFalse(emitter.completed());
        verify(redisBus, never()).unsubscribe(any());
    }

    @Test
    void doneEventShouldSendCompleteAndIgnoreLaterEvents() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        subscriber.accept(payload("done", Map.of("status", "COMPLETED")));
        subscriber.accept(payload("delta", Map.of("token", "late")));

        assertSentEvent(emitter, "done", Map.of("status", "COMPLETED"));
        assertTrue(emitter.completed());
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
    }

    @Test
    void errorEventShouldCompleteConnectionAfterIgnoringMalformedPayload() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        subscriber.accept("not-json");
        assertTrue(emitter.sentEvents().isEmpty());

        subscriber.accept(payload("error", Map.of("status", "FAILED")));
        subscriber.accept(payload("delta", Map.of("token", "late")));

        assertSentEvent(emitter, "error", Map.of("status", "FAILED"));
        assertTrue(emitter.completed());
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
    }

    @Test
    void sendFailureShouldTreatConnectionAsDisconnectedAndUnsubscribe() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        emitter.failNextSend(new IOException("broken pipe"));
        Consumer<String> subscriber = registerAndCaptureSubscriber(emitter);

        subscriber.accept(payload("delta", Map.of("token", "partial")));
        subscriber.accept(payload("delta", Map.of("token", "late")));

        assertEquals(1, emitter.sendAttempts());
        assertTrue(emitter.sentEvents().isEmpty());
        verify(redisBus).unsubscribe("pubsub:sse:user-1:stream-1");
        assertFalse(emitter.completed());
    }

    @Test
    void publishShouldSerializeEventAndUseUserScopedStreamChannel() throws Exception {
        eventBus.publish("patient-9", "stream-8", "delta", Map.of("token", "片段"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisBus).publish(eq("pubsub:sse:patient-9:stream-8"), json.capture());
        JsonNode payload = objectMapper.readTree(json.getValue());
        assertEquals("delta", payload.path("event").asText());
        assertEquals("片段", objectMapper.readTree(payload.path("dataJson").asText()).path("token").asText());
    }

    @Test
    void publishShouldNotBroadcastWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("cannot serialize") { });
        eventBus = new AiSseEventBus(redisBus, failingMapper);

        eventBus.publish("patient-9", "stream-8", "result", new Object());

        verify(redisBus, never()).publish(any(), any());
    }

    private Consumer<String> registerAndCaptureSubscriber(CapturingSseEmitter emitter) {
        eventBus.register("user-1", "stream-1", emitter);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> subscriber = ArgumentCaptor.forClass(Consumer.class);
        verify(redisBus).subscribe(eq("pubsub:sse:user-1:stream-1"), subscriber.capture());
        return subscriber.getValue();
    }

    private void assertSentEvent(CapturingSseEmitter emitter, String eventName, Object expectedData)
            throws Exception {
        assertEquals(1, emitter.sentEvents().size());
        CapturedEvent actual = emitter.sentEvents().getFirst();
        assertEquals(eventName, actual.name());
        assertEquals(objectMapper.valueToTree(expectedData), objectMapper.readTree((String) actual.data()));
    }

    private String payload(String event, Object data) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event", event,
                "dataJson", objectMapper.writeValueAsString(data)
        ));
    }

    private record CapturedEvent(String name, Object data) {
    }

    private static final class BlockingReadObjectMapper extends ObjectMapper {
        private final CountDownLatch readStarted = new CountDownLatch(1);
        private final CountDownLatch allowRead = new CountDownLatch(1);

        @Override
        public <T> T readValue(String content, Class<T> valueType) throws JsonProcessingException {
            readStarted.countDown();
            try {
                if (!allowRead.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to finish JSON parsing");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while parsing JSON", ex);
            }
            return super.readValue(content, valueType);
        }

        private boolean awaitReadStarted() throws InterruptedException {
            return readStarted.await(3, TimeUnit.SECONDS);
        }

        private void allowReadToFinish() {
            allowRead.countDown();
        }
    }

    private static final class CapturingSseEmitter extends SseEmitter {
        private static final Pattern EVENT_NAME = Pattern.compile("(?m)^event:([^\\r\\n]+)$");

        private final List<CapturedEvent> sentEvents = new CopyOnWriteArrayList<>();
        private final AtomicReference<IOException> nextSendFailure = new AtomicReference<>();
        private final AtomicInteger sendAttempts = new AtomicInteger();
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile Runnable completionHandler;
        private volatile Runnable timeoutHandler;
        private volatile Consumer<Throwable> errorHandler;

        @Override
        public void onCompletion(Runnable callback) {
            completionHandler = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutHandler = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorHandler = callback;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendAttempts.incrementAndGet();
            IOException failure = nextSendFailure.getAndSet(null);
            if (failure != null) {
                throw failure;
            }
            List<DataWithMediaType> parts = List.copyOf(builder.build());
            String eventName = null;
            Object eventData = null;
            for (int index = 0; index < parts.size(); index++) {
                Object part = parts.get(index).getData();
                if (!(part instanceof String text)) {
                    continue;
                }
                Matcher matcher = EVENT_NAME.matcher(text);
                if (matcher.find()) {
                    eventName = matcher.group(1);
                }
                if (text.endsWith("data:") && index + 1 < parts.size()) {
                    eventData = parts.get(index + 1).getData();
                }
            }
            sentEvents.add(new CapturedEvent(eventName, eventData));
        }

        @Override
        public void complete() {
            completed.set(true);
        }

        private void triggerCompletion() {
            completionHandler.run();
        }

        private void triggerTimeout() {
            timeoutHandler.run();
        }

        private void triggerError(Throwable failure) {
            errorHandler.accept(failure);
        }

        private void failNextSend(IOException failure) {
            nextSendFailure.set(failure);
        }

        private List<CapturedEvent> sentEvents() {
            return sentEvents;
        }

        private int sendAttempts() {
            return sendAttempts.get();
        }

        private boolean completed() {
            return completed.get();
        }
    }
}
