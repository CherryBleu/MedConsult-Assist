package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.MedicalImageFetcher.MedicalImagePayload;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MedicalVisionClient {
    private static final String SYSTEM_PROMPT = """
            You are a medical imaging screening model.
            Inspect the supplied medical image files and return JSON only:
            {"abnormalDetected":true,"findings":[{"abnormalType":"","location":"","riskLevel":"LOW","confidence":0.0,"suggestion":""}]}
            The result is preliminary and must be reviewed by a qualified doctor.
            """;

    private final AiProperties properties;
    private final HttpClient httpClient;

    public MedicalVisionClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds()))
                .build();
    }

    public Optional<JsonNode> detect(String imageType, List<MedicalImagePayload> images) {
        AiProperties.VisionProperties vision = properties.vision();
        if (vision == null || !StringUtils.hasText(vision.baseUrl())
                || !StringUtils.hasText(vision.apiKey()) || !StringUtils.hasText(vision.model())) {
            return Optional.empty();
        }
        if (images == null || images.isEmpty()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint(vision.baseUrl())))
                    .timeout(Duration.ofSeconds(timeoutSeconds()))
                    .header("Authorization", "Bearer " + vision.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload(vision.model(), imageType, images))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = JsonUtils.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                return Optional.empty();
            }
            return Optional.of(JsonUtils.readTree(content));
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> payload(String model, String imageType, List<MedicalImagePayload> images) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "text",
                "text", SYSTEM_PROMPT + "\nimageType=" + (imageType == null ? "" : imageType)
        ));
        for (MedicalImagePayload image : images) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", image.dataUrl())
            ));
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(message));
        payload.put("temperature", 0.1);
        return payload;
    }

    private static String endpoint(String baseUrl) {
        String normalized = baseUrl.replaceAll("/+$", "");
        return normalized.endsWith("/chat/completions") ? normalized : normalized + "/chat/completions";
    }

    private int timeoutSeconds() {
        AiProperties.VisionProperties vision = properties.vision();
        return vision == null || vision.timeoutSeconds() <= 0 ? 30 : vision.timeoutSeconds();
    }
}
