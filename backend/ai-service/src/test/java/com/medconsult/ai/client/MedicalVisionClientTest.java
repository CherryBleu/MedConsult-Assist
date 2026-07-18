package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.client.MedicalImageFetcher.MedicalImagePayload;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.util.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalVisionClientTest {

    @Test
    void detectShouldSendOpenAiVisionPayloadAndParseModelJson() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        String modelContent = JsonUtils.toJson(Map.of(
                "abnormalDetected", true,
                "findings", List.of(Map.of("riskLevel", "HIGH", "location", "left lung"))
        ));
        String envelope = JsonUtils.toJson(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", modelContent)))
        ));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, envelope);
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
            MedicalVisionClient client = new MedicalVisionClient(properties(baseUrl, "secret", "vision-model", 2));

            Optional<JsonNode> result = client.detect("CT", List.of(
                    image("one", "data:image/png;base64,AQ=="),
                    image("two", "data:application/dicom;base64,Ag==")
            ));

            assertTrue(result.isPresent());
            assertTrue(result.orElseThrow().path("abnormalDetected").asBoolean());
            assertEquals("HIGH", result.orElseThrow().path("findings").path(0).path("riskLevel").asText());
            assertEquals("POST", method.get());
            assertEquals("Bearer secret", authorization.get());

            JsonNode payload = JsonUtils.readTree(requestBody.get());
            assertEquals("vision-model", payload.path("model").asText());
            assertEquals("json_object", payload.path("response_format").path("type").asText());
            assertEquals(0.1, payload.path("temperature").asDouble(), 0.0001);
            JsonNode content = payload.path("messages").path(0).path("content");
            assertEquals(3, content.size());
            assertTrue(content.path(0).path("text").asText().contains("imageType=CT"));
            assertEquals("data:image/png;base64,AQ==",
                    content.path(1).path("image_url").path("url").asText());
            assertEquals("data:application/dicom;base64,Ag==",
                    content.path(2).path("image_url").path("url").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void detectShouldUseExistingChatCompletionsEndpointAndAllowNullImageType() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String envelope = JsonUtils.toJson(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "{}")))
        ));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/custom/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, envelope);
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort()
                    + "/custom/chat/completions/";
            MedicalVisionClient client = new MedicalVisionClient(properties(baseUrl, "key", "model", 0));

            Optional<JsonNode> result = client.detect(null, List.of(image("one", "data:image/jpeg;base64,AQ==")));

            assertTrue(result.isPresent());
            JsonNode payload = JsonUtils.readTree(requestBody.get());
            assertTrue(payload.path("messages").path(0).path("content").path(0)
                    .path("text").asText().endsWith("imageType="));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void detectShouldReturnEmptyWhenVisionConfigurationIsIncomplete() {
        MedicalImagePayload image = image("one", "data:image/png;base64,AQ==");

        assertTrue(new MedicalVisionClient(properties(null)).detect("CT", List.of(image)).isEmpty());
        assertTrue(new MedicalVisionClient(properties("", "key", "model", 1))
                .detect("CT", List.of(image)).isEmpty());
        assertTrue(new MedicalVisionClient(properties("http://vision", "", "model", 1))
                .detect("CT", List.of(image)).isEmpty());
        assertTrue(new MedicalVisionClient(properties("http://vision", "key", "", 1))
                .detect("CT", List.of(image)).isEmpty());
    }

    @Test
    void detectShouldReturnEmptyWhenNoImagesAreSupplied() {
        MedicalVisionClient client = new MedicalVisionClient(properties("http://vision", "key", "model", 1));

        assertTrue(client.detect("CT", null).isEmpty());
        assertTrue(client.detect("CT", List.of()).isEmpty());
    }

    @Test
    void detectShouldReturnEmptyForNonSuccessfulModelResponse() throws Exception {
        HttpServer server = respondingServer("/chat/completions", 429, "rate limited");
        try {
            MedicalVisionClient client = new MedicalVisionClient(properties(serverUrl(server), "key", "model", 2));

            assertTrue(client.detect("MRI", List.of(image("one", "data:image/png;base64,AQ=="))).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void detectShouldReturnEmptyForBlankModelContent() throws Exception {
        String envelope = JsonUtils.toJson(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "  ")))
        ));
        HttpServer server = respondingServer("/chat/completions", 200, envelope);
        try {
            MedicalVisionClient client = new MedicalVisionClient(properties(serverUrl(server), "key", "model", 2));

            assertTrue(client.detect("MRI", List.of(image("one", "data:image/png;base64,AQ=="))).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void detectShouldReturnEmptyWhenModelResponseIsMalformedJson() throws Exception {
        HttpServer server = respondingServer("/chat/completions", 200, "not-json");
        try {
            MedicalVisionClient client = new MedicalVisionClient(properties(serverUrl(server), "key", "model", 2));

            assertTrue(client.detect("MRI", List.of(image("one", "data:image/png;base64,AQ=="))).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void detectShouldReturnEmptyWhenVisionEndpointCannotBeReached() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            unusedPort = socket.getLocalPort();
        }
        MedicalVisionClient client = new MedicalVisionClient(properties(
                "http://127.0.0.1:" + unusedPort, "key", "model", 1));

        Optional<JsonNode> result = client.detect("CT", List.of(image("one", "data:image/png;base64,AQ==")));

        assertTrue(result.isEmpty());
    }

    @Test
    void detectShouldRestoreInterruptFlagWhenRequestIsInterrupted() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestArrived.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "{}");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            MedicalVisionClient client = new MedicalVisionClient(properties(serverUrl(server), "key", "model", 5));
            AtomicReference<Optional<JsonNode>> result = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = new Thread(() -> {
                result.set(client.detect("CT", List.of(image("one", "data:image/png;base64,AQ=="))));
                interrupted.set(Thread.currentThread().isInterrupted());
            });

            caller.start();
            assertTrue(requestArrived.await(2, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(2000);

            assertFalse(caller.isAlive());
            assertTrue(result.get().isEmpty());
            assertTrue(interrupted.get());
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }

    private static HttpServer respondingServer(String path, int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> respond(exchange, status, body));
        server.start();
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String serverUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static MedicalImagePayload image(String source, String dataUrl) {
        return new MedicalImagePayload(source, "image/png", 1, dataUrl);
    }

    private static AiProperties properties(String baseUrl, String apiKey, String model, int timeoutSeconds) {
        return properties(new AiProperties.VisionProperties(baseUrl, apiKey, model, timeoutSeconds, 1024));
    }

    private static AiProperties properties(AiProperties.VisionProperties vision) {
        return new AiProperties(null, null, null, null, null, null, vision, null, null, null, null);
    }
}
