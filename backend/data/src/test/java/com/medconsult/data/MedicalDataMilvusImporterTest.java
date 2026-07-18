package com.medconsult.data;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalDataMilvusImporterTest {

    @Test
    void configShouldDefaultMilvusMetricToCosineForExistingImports() {
        MedicalDataMilvusImporter.Config config = MedicalDataMilvusImporter.Config.from(Map.of());

        assertEquals("COSINE", config.milvusMetricType());
    }

    @Test
    void configShouldReadMilvusMetricFromSameEnvironmentVariableAsRuntimeSearch() {
        MedicalDataMilvusImporter.Config config = MedicalDataMilvusImporter.Config.from(Map.of(
                "MILVUS_METRIC_TYPE", "l2"
        ));

        assertEquals("L2", config.milvusMetricType());
    }

    @Test
    void euclideanAliasShouldCreateMilvusL2Index() {
        MedicalDataMilvusImporter.Config config = MedicalDataMilvusImporter.Config.from(Map.of(
                "MILVUS_METRIC_TYPE", "euclidean"
        ));

        assertEquals("L2", config.milvusMetricType());
    }

    @Test
    void importerShouldFlushMilvusCollectionAfterUpsert() throws Exception {
        ImporterFixture fixture = importerFixture(false);

        try {
            MedicalDataMilvusImporter.run(fixture.config());
        } finally {
            fixture.close();
        }

        int upsertIndex = fixture.paths().indexOf("/v2/vectordb/entities/upsert");
        int flushIndex = fixture.paths().indexOf("/v2/vectordb/collections/flush");
        assertTrue(upsertIndex >= 0);
        assertTrue(flushIndex > upsertIndex);
    }

    @Test
    void importerShouldTolerateMissingMilvusRestFlushEndpoint() throws Exception {
        ImporterFixture fixture = importerFixture(true);

        try {
            assertDoesNotThrow(() -> MedicalDataMilvusImporter.run(fixture.config()));
        } finally {
            fixture.close();
        }

        assertTrue(fixture.paths().contains("/v2/vectordb/collections/flush"));
    }

    private static ImporterFixture importerFixture(boolean flushReturnsNotFound) throws Exception {
        Path input = Files.createTempFile("medical-data", ".json");
        Files.writeString(input, """
                [
                  {
                    "_id": "d-1",
                    "name": "肺炎",
                    "symptom": ["咳嗽", "发热"],
                    "desc": "肺部感染"
                  }
                ]
                """, StandardCharsets.UTF_8);

        CopyOnWriteArrayList<String> paths = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            if (flushReturnsNotFound && "/v2/vectordb/collections/flush".equals(path)) {
                byte[] bytes = "404 page not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }
            String response = switch (path) {
                case "/v1/embeddings" -> "{\"data\":[{\"embedding\":[0.1,0.2]}]}";
                case "/v2/vectordb/collections/has" -> "{\"code\":0,\"data\":{\"has\":false}}";
                default -> "{\"code\":0,\"data\":{}}";
            };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        MedicalDataMilvusImporter.Config config = MedicalDataMilvusImporter.Config.from(Map.ofEntries(
                Map.entry("MEDICAL_DATA_INPUT", input.toString()),
                Map.entry("EMBEDDING_BASE_URL", baseUrl + "/v1"),
                Map.entry("EMBEDDING_API_KEY", "test-key"),
                Map.entry("EMBEDDING_MODEL", "test-embedding"),
                Map.entry("EMBEDDING_DIMENSION", "2"),
                Map.entry("EMBEDDING_BATCH_SIZE", "1"),
                Map.entry("MILVUS_URI", baseUrl),
                Map.entry("MILVUS_TOKEN", ""),
                Map.entry("MILVUS_DATABASE", "medical"),
                Map.entry("MILVUS_COLLECTION", "data"),
                Map.entry("HTTP_TIMEOUT_SECONDS", "5"),
                Map.entry("MAX_RETRIES", "1")
        ));
        return new ImporterFixture(config, paths, server, input);
    }

    private record ImporterFixture(
            MedicalDataMilvusImporter.Config config,
            CopyOnWriteArrayList<String> paths,
            HttpServer server,
            Path input
    ) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            server.stop(0);
            Files.deleteIfExists(input);
        }
    }
}
