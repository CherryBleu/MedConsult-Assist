package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.util.JsonUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class OpenAiCompatibleClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AiProperties properties;
    private final ChatLanguageModel jsonChatModel;
    private final ChatLanguageModel textChatModel;
    private final StreamingChatLanguageModel streamingJsonChatModel;
    private final StreamingChatLanguageModel streamingTextChatModel;
    private final EmbeddingModel embeddingModel;

    public OpenAiCompatibleClient(AiProperties properties) {
        this.properties = properties;
        this.jsonChatModel = createChatModel(true);
        this.textChatModel = createChatModel(false);
        this.streamingJsonChatModel = createStreamingChatModel(true);
        this.streamingTextChatModel = createStreamingChatModel(false);
        this.embeddingModel = createEmbeddingModel();
    }

    public Optional<JsonNode> chatJson(String systemPrompt, String userPrompt) {
        long started = System.currentTimeMillis();
        try {
            ChatCallResult chatResult = chat(systemPrompt, userPrompt, true);
            Optional<JsonNode> result = chatResult.content().map(JsonUtils::readTree);
            log.info("[AI-TIMER] llm.chatJson={}ms success={} reason={} error={}",
                    elapsed(started), result.isPresent(), chatResult.reason(), chatResult.error());
            return result;
        } catch (RuntimeException ex) {
            log.info("[AI-TIMER] llm.chatJson={}ms success=false reason=parse_or_unhandled error={}",
                    elapsed(started), describe(ex));
            throw ex;
        }
    }

    public Optional<String> chatText(String systemPrompt, String userPrompt) {
        long started = System.currentTimeMillis();
        ChatCallResult result = chat(systemPrompt, userPrompt, false);
        log.info("[AI-TIMER] llm.chatText={}ms success={} reason={} error={}",
                elapsed(started), result.content().isPresent(), result.reason(), result.error());
        return result.content();
    }

    public Optional<JsonNode> chatJsonStream(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        long started = System.currentTimeMillis();
        try {
            ChatCallResult chatResult = stream(systemPrompt, userPrompt, true, tokenConsumer);
            Optional<JsonNode> result = chatResult.content().map(JsonUtils::readTree);
            log.info("[AI-TIMER] llm.chatJsonStream={}ms success={} reason={} error={}",
                    elapsed(started), result.isPresent(), chatResult.reason(), chatResult.error());
            return result;
        } catch (RuntimeException ex) {
            log.info("[AI-TIMER] llm.chatJsonStream={}ms success=false reason=parse_or_unhandled error={}",
                    elapsed(started), describe(ex));
            throw ex;
        }
    }

    public Optional<String> chatTextStream(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        long started = System.currentTimeMillis();
        ChatCallResult result = stream(systemPrompt, userPrompt, false, tokenConsumer);
        log.info("[AI-TIMER] llm.chatTextStream={}ms success={} reason={} error={}",
                elapsed(started), result.content().isPresent(), result.reason(), result.error());
        return result.content();
    }

    public Optional<List<Float>> embedOne(String input) {
        long started = System.currentTimeMillis();
        AiProperties.EmbeddingProperties embedding = properties.embedding();
        if (embedding == null || isBlank(embedding.apiKey()) || isBlank(embedding.baseUrl()) || isBlank(embedding.model())) {
            log.info("[AI-TIMER] embedding.embedOne={}ms success=false reason=config_missing", elapsed(started));
            return Optional.empty();
        }
        try {
            if (embeddingModel == null) {
                log.info("[AI-TIMER] embedding.embedOne={}ms success=false reason=model_missing", elapsed(started));
                return Optional.empty();
            }
            Response<Embedding> response = embeddingModel.embed(input);
            List<Float> vector = response.content().vectorAsList();
            Optional<List<Float>> result = vector.isEmpty() ? Optional.empty() : Optional.of(vector);
            log.info("[AI-TIMER] embedding.embedOne={}ms success={} dimensions={}",
                    elapsed(started), result.isPresent(), vector.size());
            return result;
        } catch (RuntimeException ex) {
            log.info("[AI-TIMER] embedding.embedOne={}ms success=false error={}", elapsed(started), ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private ChatCallResult chat(String systemPrompt, String userPrompt, boolean jsonResponse) {
        AiProperties.LlmProperties llm = properties.llm();
        if (llm == null || isBlank(llm.apiKey()) || isBlank(llm.baseUrl()) || isBlank(llm.model())) {
            return ChatCallResult.failed("config_missing", "");
        }
        try {
            ChatLanguageModel model = jsonResponse ? jsonChatModel : textChatModel;
            if (model == null) {
                return ChatCallResult.failed("model_missing", "");
            }
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );
            Response<AiMessage> response = model.generate(messages);
            String content = response.content().text();
            if (content == null || content.isBlank()) {
                return ChatCallResult.failed("empty_content", "");
            }
            return ChatCallResult.success(content);
        } catch (RuntimeException ex) {
            return ChatCallResult.failed("exception", describe(ex));
        }
    }

    private ChatCallResult stream(String systemPrompt, String userPrompt, boolean jsonResponse, Consumer<String> tokenConsumer) {
        AiProperties.LlmProperties llm = properties.llm();
        if (llm == null || isBlank(llm.apiKey()) || isBlank(llm.baseUrl()) || isBlank(llm.model())) {
            return ChatCallResult.failed("config_missing", "");
        }
        try {
            StreamingChatLanguageModel model = jsonResponse ? streamingJsonChatModel : streamingTextChatModel;
            if (model == null) {
                return ChatCallResult.failed("model_missing", "");
            }
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );
            StringBuilder content = new StringBuilder();
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            model.generate(messages, new StreamingResponseHandler<>() {
                @Override
                public void onNext(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    content.append(token);
                    if (tokenConsumer != null) {
                        tokenConsumer.accept(token);
                    }
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    done.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    error.set(throwable);
                    done.countDown();
                }
            });
            boolean completed = done.await(Math.max(5, llm.timeoutSeconds()) + 5L, TimeUnit.SECONDS);
            if (!completed) {
                return ChatCallResult.failed("timeout", "");
            }
            if (error.get() != null) {
                return ChatCallResult.failed("exception", describe(error.get()));
            }
            if (content.isEmpty()) {
                return ChatCallResult.failed("empty_content", "");
            }
            return ChatCallResult.success(content.toString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ChatCallResult.failed("interrupted", describe(ex));
        } catch (RuntimeException ex) {
            return ChatCallResult.failed("exception", describe(ex));
        }
    }

    private ChatLanguageModel createChatModel(boolean jsonResponse) {
        AiProperties.LlmProperties llm = properties.llm();
        if (llm == null || isBlank(llm.apiKey()) || isBlank(llm.baseUrl()) || isBlank(llm.model())) {
            return null;
        }
        return OpenAiChatModel.builder()
                .baseUrl(openAiBaseUrl(llm.baseUrl()))
                .apiKey(llm.apiKey())
                .modelName(llm.model())
                .temperature(jsonResponse ? 0.1 : 0.2)
                .responseFormat(jsonResponse ? "json_object" : null)
                .timeout(Duration.ofSeconds(Math.max(5, llm.timeoutSeconds())))
                .maxRetries(0)
                .build();
    }

    private StreamingChatLanguageModel createStreamingChatModel(boolean jsonResponse) {
        AiProperties.LlmProperties llm = properties.llm();
        if (llm == null || isBlank(llm.apiKey()) || isBlank(llm.baseUrl()) || isBlank(llm.model())) {
            return null;
        }
        return OpenAiStreamingChatModel.builder()
                .baseUrl(openAiBaseUrl(llm.baseUrl()))
                .apiKey(llm.apiKey())
                .modelName(llm.model())
                .temperature(jsonResponse ? 0.1 : 0.2)
                .responseFormat(jsonResponse ? "json_object" : null)
                .timeout(Duration.ofSeconds(Math.max(5, llm.timeoutSeconds())))
                .build();
    }

    private EmbeddingModel createEmbeddingModel() {
        AiProperties.EmbeddingProperties embedding = properties.embedding();
        if (embedding == null || isBlank(embedding.apiKey()) || isBlank(embedding.baseUrl()) || isBlank(embedding.model())) {
            return null;
        }
        return OpenAiEmbeddingModel.builder()
                .baseUrl(openAiBaseUrl(embedding.baseUrl()))
                .apiKey(embedding.apiKey())
                .modelName(embedding.model())
                .timeout(Duration.ofSeconds(Math.max(5, embedding.timeoutSeconds())))
                .maxRetries(0)
                .build();
    }

    private static String trimRightSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static String openAiBaseUrl(String value) {
        String baseUrl = trimRightSlash(value);
        return baseUrl + "/";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }

    private static String describe(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180);
        }
        return root.getClass().getSimpleName() + ":" + normalized;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private record ChatCallResult(Optional<String> content, String reason, String error) {
        static ChatCallResult success(String content) {
            return new ChatCallResult(Optional.of(content), "ok", "");
        }

        static ChatCallResult failed(String reason, String error) {
            return new ChatCallResult(Optional.empty(), reason, error);
        }
    }
}
