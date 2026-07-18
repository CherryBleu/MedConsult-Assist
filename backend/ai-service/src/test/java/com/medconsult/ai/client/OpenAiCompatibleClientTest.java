package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.config.AiProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleClientTest {

    @Test
    void chatJsonShouldParseContentAndPassPromptsInOrder() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList())).thenReturn(Response.from(AiMessage.from("{\"accepted\":true}")));
        OpenAiCompatibleClient client = clientWith("jsonChatModel", model);

        JsonNode result = client.chatJson("system rules", "patient question").orElseThrow();

        assertTrue(result.path("accepted").asBoolean());
        assertPrompts(model, "system rules", "patient question");
    }

    @Test
    void chatTextShouldReturnContentAndPassPromptsInOrder() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList())).thenReturn(Response.from(AiMessage.from("model answer")));
        OpenAiCompatibleClient client = clientWith("textChatModel", model);

        assertEquals(Optional.of("model answer"), client.chatText("system", "user"));
        assertPrompts(model, "system", "user");
    }

    @ParameterizedTest
    @MethodSource("missingLlmConfigurations")
    void chatTextShouldShortCircuitIncompleteConfiguration(AiProperties properties) {
        assertTrue(new OpenAiCompatibleClient(properties).chatText("system", "user").isEmpty());
    }

    @Test
    void chatTextShouldReturnEmptyWhenConfiguredModelIsMissing() {
        OpenAiCompatibleClient client = clientWith("textChatModel", null);

        assertTrue(client.chatText("system", "user").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("blankModelContents")
    void chatTextShouldRejectNullOrBlankModelContent(String content) {
        AiMessage message = mock(AiMessage.class);
        when(message.text()).thenReturn(content);
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList())).thenReturn(Response.from(message));

        assertTrue(clientWith("textChatModel", model).chatText("system", "user").isEmpty());
    }

    @Test
    void chatTextShouldContainModelRuntimeFailure() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList())).thenThrow(new IllegalStateException("model unavailable"));

        assertTrue(clientWith("textChatModel", model).chatText("system", "user").isEmpty());
    }

    @Test
    void chatJsonShouldPropagateMalformedJsonFailure() {
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        when(model.generate(anyList())).thenReturn(Response.from(AiMessage.from("not-json")));

        assertThrows(RuntimeException.class,
                () -> clientWith("jsonChatModel", model).chatJson("system", "user"));
    }

    @ParameterizedTest
    @MethodSource("missingEmbeddingConfigurations")
    void embedOneShouldShortCircuitIncompleteConfiguration(AiProperties properties) {
        assertTrue(new OpenAiCompatibleClient(properties).embedOne("query").isEmpty());
    }

    @Test
    void embedOneShouldReturnEmptyWhenConfiguredModelIsMissing() {
        assertTrue(clientWith("embeddingModel", null).embedOne("query").isEmpty());
    }

    @Test
    void embedOneShouldReturnNonEmptyVector() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed("query")).thenReturn(Response.from(Embedding.from(new float[]{0.25f, 0.75f})));

        assertEquals(Optional.of(List.of(0.25f, 0.75f)),
                clientWith("embeddingModel", model).embedOne("query"));
    }

    @Test
    void embedOneShouldRejectEmptyVector() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed("query")).thenReturn(Response.from(Embedding.from(new float[0])));

        assertTrue(clientWith("embeddingModel", model).embedOne("query").isEmpty());
    }

    @Test
    void embedOneShouldContainModelRuntimeFailure() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed("query")).thenThrow(new IllegalArgumentException("bad input"));

        assertTrue(clientWith("embeddingModel", model).embedOne("query").isEmpty());
    }

    @Test
    void chatJsonStreamShouldIgnoreEmptyTokensDeliverValidTokensAndParseContent() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        answerStream(model, handler -> {
            handler.onNext(null);
            handler.onNext("");
            handler.onNext("{\"accepted\":");
            handler.onNext("true}");
            handler.onComplete(Response.from(AiMessage.from("ignored")));
        });
        List<String> deliveredTokens = new ArrayList<>();

        JsonNode result = clientWith("streamingJsonChatModel", model)
                .chatJsonStream("system", "user", deliveredTokens::add)
                .orElseThrow();

        assertTrue(result.path("accepted").asBoolean());
        assertEquals(List.of("{\"accepted\":", "true}"), deliveredTokens);
        assertStreamPrompts(model, "system", "user");
    }

    @Test
    void chatTextStreamShouldSupportMissingConsumerAndCompleteSuccessfully() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        answerStream(model, handler -> {
            handler.onNext("plain answer");
            handler.onComplete(Response.from(AiMessage.from("ignored")));
        });

        assertEquals(Optional.of("plain answer"),
                clientWith("streamingTextChatModel", model).chatTextStream("system", "user", null));
    }

    @ParameterizedTest
    @MethodSource("missingLlmConfigurations")
    void chatTextStreamShouldShortCircuitIncompleteConfiguration(AiProperties properties) {
        List<String> deliveredTokens = new ArrayList<>();

        assertTrue(new OpenAiCompatibleClient(properties)
                .chatTextStream("system", "user", deliveredTokens::add).isEmpty());
        assertTrue(deliveredTokens.isEmpty());
    }

    @Test
    void chatTextStreamShouldReturnEmptyWhenConfiguredModelIsMissing() {
        assertTrue(clientWith("streamingTextChatModel", null)
                .chatTextStream("system", "user", ignored -> { }).isEmpty());
    }

    @Test
    void chatTextStreamShouldReturnEmptyWhenModelCompletesWithoutContent() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        answerStream(model, handler -> {
            handler.onNext(null);
            handler.onNext("");
            handler.onComplete(Response.from(AiMessage.from("ignored")));
        });

        assertTrue(clientWith("streamingTextChatModel", model)
                .chatTextStream("system", "user", ignored -> { }).isEmpty());
    }

    @Test
    void chatTextStreamShouldContainAsynchronousFailure() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        answerStream(model, handler -> handler.onError(
                new CompletionException(new IllegalArgumentException("stream failed"))));

        assertTrue(clientWith("streamingTextChatModel", model)
                .chatTextStream("system", "user", ignored -> { }).isEmpty());
    }

    @Test
    void chatTextStreamShouldContainSynchronousModelFailure() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        doThrow(new IllegalStateException("startup failed"))
                .when(model).generate(anyList(), any());

        assertTrue(clientWith("streamingTextChatModel", model)
                .chatTextStream("system", "user", ignored -> { }).isEmpty());
    }

    @Test
    void chatJsonStreamShouldPropagateMalformedJsonFailure() {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        answerStream(model, handler -> {
            handler.onNext("not-json");
            handler.onComplete(Response.from(AiMessage.from("ignored")));
        });

        assertThrows(RuntimeException.class, () -> clientWith("streamingJsonChatModel", model)
                .chatJsonStream("system", "user", ignored -> { }));
    }

    @Test
    void chatTextStreamShouldRestoreInterruptFlagWithoutWaitingForTimeout() throws Exception {
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        CountDownLatch generateCalled = new CountDownLatch(1);
        doAnswer(invocation -> {
            generateCalled.countDown();
            return null;
        }).when(model).generate(anyList(), any());
        OpenAiCompatibleClient client = clientWith("streamingTextChatModel", model);
        AtomicReference<Optional<String>> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            result.set(client.chatTextStream("system", "user", ignored -> { }));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        caller.setDaemon(true);

        caller.start();
        assertTrue(generateCalled.await(2, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(2_000);

        assertFalse(caller.isAlive());
        assertEquals(Optional.empty(), result.get());
        assertTrue(interrupted.get());
    }

    @Test
    void constructorShouldBuildEveryConfiguredModelAtMinimumAndExplicitTimeouts() {
        OpenAiCompatibleClient minimumTimeout = new OpenAiCompatibleClient(validProperties(0, 0));
        OpenAiCompatibleClient explicitTimeout = new OpenAiCompatibleClient(validProperties(8, 9));

        assertInstanceOf(OpenAiChatModel.class, field(minimumTimeout, "jsonChatModel"));
        assertInstanceOf(OpenAiChatModel.class, field(minimumTimeout, "textChatModel"));
        assertInstanceOf(OpenAiStreamingChatModel.class, field(minimumTimeout, "streamingJsonChatModel"));
        assertInstanceOf(OpenAiStreamingChatModel.class, field(minimumTimeout, "streamingTextChatModel"));
        assertInstanceOf(OpenAiEmbeddingModel.class, field(minimumTimeout, "embeddingModel"));
        assertNotNull(field(explicitTimeout, "jsonChatModel"));
        assertNotNull(field(explicitTimeout, "embeddingModel"));
    }

    @Test
    void helpersShouldNormalizeBaseUrlAndDescribeRootFailuresSafely() {
        assertEquals("", invoke("trimRightSlash", (Object) null));
        assertEquals("https://models.example/v1", invoke("trimRightSlash", "https://models.example/v1///"));
        assertEquals("https://models.example/v1/", invoke("openAiBaseUrl", "https://models.example/v1///"));

        IllegalArgumentException root = new IllegalArgumentException("  invalid\n  response  ");
        CompletionException wrapper = new CompletionException(root);
        assertSame(root, invoke("rootCause", wrapper));
        assertEquals("IllegalArgumentException:invalid response", invoke("describe", wrapper));
        assertEquals("IllegalStateException", invoke("describe", new IllegalStateException(" ")));
        assertEquals("IllegalArgumentException:" + "x".repeat(180),
                invoke("describe", new IllegalArgumentException("x".repeat(181))));
    }

    @SuppressWarnings("unchecked")
    private static void assertPrompts(ChatLanguageModel model, String systemPrompt, String userPrompt) {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(model).generate(captor.capture());
        List<ChatMessage> messages = captor.getValue();
        assertEquals(2, messages.size());
        assertEquals(systemPrompt, assertInstanceOf(SystemMessage.class, messages.get(0)).text());
        assertEquals(userPrompt, assertInstanceOf(UserMessage.class, messages.get(1)).singleText());
    }

    @SuppressWarnings("unchecked")
    private static void assertStreamPrompts(StreamingChatLanguageModel model,
                                            String systemPrompt,
                                            String userPrompt) {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(model).generate(captor.capture(), any());
        List<ChatMessage> messages = captor.getValue();
        assertEquals(2, messages.size());
        assertEquals(systemPrompt, assertInstanceOf(SystemMessage.class, messages.get(0)).text());
        assertEquals(userPrompt, assertInstanceOf(UserMessage.class, messages.get(1)).singleText());
    }

    private static void answerStream(StreamingChatLanguageModel model,
                                     Consumer<StreamingResponseHandler<AiMessage>> answer) {
        doAnswer(invocation -> {
            answer.accept(invocation.getArgument(1));
            return null;
        }).when(model).generate(anyList(), any());
    }

    private static OpenAiCompatibleClient clientWith(String fieldName, Object model) {
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(validProperties(1, 1));
        ReflectionTestUtils.setField(client, fieldName, model);
        return client;
    }

    private static Object field(OpenAiCompatibleClient client, String fieldName) {
        return ReflectionTestUtils.getField(client, fieldName);
    }

    private static <T> T invoke(String methodName, Object argument) {
        return ReflectionTestUtils.invokeMethod(OpenAiCompatibleClient.class, methodName, argument);
    }

    private static Stream<String> blankModelContents() {
        return Stream.of(null, "   ");
    }

    private static Stream<AiProperties> missingLlmConfigurations() {
        return Stream.of(
                properties(null, null),
                properties(new AiProperties.LlmProperties("https://llm.example", null, "chat", 1), null),
                properties(new AiProperties.LlmProperties(" ", "key", "chat", 1), null),
                properties(new AiProperties.LlmProperties("https://llm.example", "key", "", 1), null)
        );
    }

    private static Stream<AiProperties> missingEmbeddingConfigurations() {
        return Stream.of(
                properties(null, null),
                properties(null, new AiProperties.EmbeddingProperties("https://embedding.example", null, "embed", 1)),
                properties(null, new AiProperties.EmbeddingProperties(" ", "key", "embed", 1)),
                properties(null, new AiProperties.EmbeddingProperties("https://embedding.example", "key", "", 1))
        );
    }

    private static AiProperties validProperties(int llmTimeout, int embeddingTimeout) {
        return properties(
                new AiProperties.LlmProperties("https://llm.example/v1///", "key", "chat", llmTimeout),
                new AiProperties.EmbeddingProperties(
                        "https://embedding.example/v1///", "key", "embed", embeddingTimeout)
        );
    }

    private static AiProperties properties(AiProperties.LlmProperties llm,
                                           AiProperties.EmbeddingProperties embedding) {
        return new AiProperties(llm, embedding, null, null, null, null, null, null, null, null, null);
    }
}
