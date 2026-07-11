package com.medconsult.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MedicalDataMilvusImporter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VECTOR_TEXT_FIELDS = Set.of("name", "symptom", "desc");
    private static final Set<String> INTERNAL_FIELDS = Set.of("_id");
    private static final Set<String> METADATA_FIELDS = Set.of(
            "category", "prevent", "cause", "yibao_status", "get_prob", "easy_get", "get_way",
            "acompany", "cure_department", "cure_way", "cure_lasttime", "cured_prob",
            "common_drug", "cost_money", "check", "do_eat", "not_eat", "recommand_eat",
            "recommand_drug", "drug_detail"
    );
    private static final List<String> TRIMMABLE_METADATA_FIELDS = List.of(
            "drug_detail", "cause", "prevent", "do_eat", "not_eat", "recommand_eat",
            "check", "cost_money", "acompany"
    );

    public static void main(String[] args) throws Exception {
        configureConsole(resolveConsoleCharset());
        Config config = Config.fromEnv();
        System.out.println("input=" + config.inputFile());
        System.out.println("milvus=" + config.milvusUri() + ", db=" + config.milvusDatabase()
                + ", collection=" + config.milvusCollection());
        System.out.println("embedding model=" + config.embeddingModel() + ", dimension=" + config.embeddingDimension());

        OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(config);
        MilvusRestClient milvusClient = new MilvusRestClient(config);
        if (!config.dryRun()) {
            milvusClient.ensureDatabase();
            milvusClient.ensureCollection();
        }

        ImportStats stats = importData(config, embeddingClient, milvusClient);
        System.out.printf(Locale.ROOT, "完成：读取 %d 条，跳过 %d 条，写入 %d 条。%n",
                stats.read(), stats.skipped(), stats.upserted());
    }

    private static Charset resolveConsoleCharset() {
        String override = System.getenv("CONSOLE_CHARSET");
        if (override != null && !override.isBlank()) {
            return Charset.forName(override.trim());
        }
        if (System.console() != null) {
            return System.console().charset();
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Charset.forName("GBK");
        }
        return Charset.defaultCharset();
    }

    private static void configureConsole(Charset charset) {
        System.setOut(new PrintStream(System.out, true, charset));
        System.setErr(new PrintStream(System.err, true, charset));
    }

    private static ImportStats importData(Config config, OpenAiEmbeddingClient embeddingClient, MilvusRestClient milvusClient)
            throws Exception {
        List<DiseaseRecord> pending = new ArrayList<>(config.embeddingBatchSize());
        ImportStats stats = new ImportStats();

        try (InputStream input = Files.newInputStream(config.inputFile());
             JsonParser parser = MAPPER.createParser(input)) {
            if (parser.nextToken() == null || !parser.currentToken().isStructStart()) {
                throw new IllegalArgumentException("输入文件必须是 JSON 数组：" + config.inputFile());
            }

            while (parser.nextToken() != null && !parser.currentToken().isStructEnd()) {
                JsonNode node = MAPPER.readTree(parser);
                stats.read++;
                DiseaseRecord record = DiseaseRecord.fromJson(node, config);
                if (record.text().isBlank()) {
                    stats.skipped++;
                    continue;
                }
                pending.add(record);
                if (pending.size() >= config.embeddingBatchSize()) {
                    stats.upserted += flushBatch(config, embeddingClient, milvusClient, pending);
                    pending.clear();
                }
                if (config.limit() > 0 && stats.read >= config.limit()) {
                    break;
                }
            }
        }

        if (!pending.isEmpty()) {
            stats.upserted += flushBatch(config, embeddingClient, milvusClient, pending);
        }
        return stats;
    }

    private static int flushBatch(
            Config config,
            OpenAiEmbeddingClient embeddingClient,
            MilvusRestClient milvusClient,
            List<DiseaseRecord> records
    ) throws Exception {
        List<String> texts = records.stream().map(DiseaseRecord::text).toList();
        List<List<Float>> embeddings = config.dryRun()
                ? fakeEmbeddings(records.size(), config.embeddingDimension())
                : embeddingClient.embed(texts);

        if (embeddings.size() != records.size()) {
            throw new IllegalStateException("Embedding 返回数量不一致：expected=" + records.size()
                    + ", actual=" + embeddings.size());
        }

        ArrayNode entities = MAPPER.createArrayNode();
        for (int i = 0; i < records.size(); i++) {
            List<Float> vector = embeddings.get(i);
            if (vector.size() != config.embeddingDimension()) {
                throw new IllegalStateException("向量维度不一致：id=" + records.get(i).id()
                        + ", expected=" + config.embeddingDimension() + ", actual=" + vector.size());
            }
            DiseaseRecord record = records.get(i);
            ObjectNode entity = entities.addObject();
            entity.put("id", record.id());
            entity.put("name", abbreviate(record.name(), config.nameMaxLength()));
            entity.put("text", abbreviate(record.text(), config.textMaxLength()));
            ArrayNode vectorNode = entity.putArray("vector");
            vector.forEach(vectorNode::add);
            entity.set("metadata", record.metadata());
        }

        if (!config.dryRun()) {
            milvusClient.upsert(entities);
        }
        System.out.printf(Locale.ROOT, "%s %d 条，累计至 source id=%s%n",
                config.dryRun() ? "DRY_RUN 生成" : "已写入", records.size(), records.get(records.size() - 1).id());
        return records.size();
    }

    private static List<List<Float>> fakeEmbeddings(int count, int dimension) {
        List<List<Float>> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            List<Float> vector = new ArrayList<>(dimension);
            for (int j = 0; j < dimension; j++) {
                vector.add(0.0f);
            }
            result.add(vector);
        }
        return result;
    }

    private record DiseaseRecord(String id, String name, String text, ObjectNode metadata) {
        static DiseaseRecord fromJson(JsonNode node, Config config) {
            String id = readSourceId(node);
            String name = node.path("name").asText("").trim();
            String symptom = joinText(node.path("symptom"));
            String desc = node.path("desc").asText("").trim();
            String text = buildVectorText(name, symptom, desc, config.embeddingTextMaxChars());

            ObjectNode metadata = MAPPER.createObjectNode();
            metadata.put("source_id", id);
            metadata.put("source_file", config.inputFile().getFileName().toString());
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                if (METADATA_FIELDS.contains(fieldName)
                        && !VECTOR_TEXT_FIELDS.contains(fieldName)
                        && !INTERNAL_FIELDS.contains(fieldName)) {
                    metadata.set(fieldName, copyMetadataValue(entry.getValue(), config.metadataTextMaxChars()));
                }
            });
            enforceMetadataLimit(metadata, config);
            return new DiseaseRecord(id, name, text, metadata);
        }

        private static String buildVectorText(String name, String symptom, String desc, int maxChars) {
            List<String> parts = new ArrayList<>();
            if (!name.isBlank()) {
                parts.add("疾病名称：" + name);
            }
            if (!symptom.isBlank()) {
                parts.add("症状：" + symptom);
            }
            if (!desc.isBlank()) {
                parts.add("疾病描述：" + desc);
            }
            return abbreviate(String.join("\n", parts), maxChars);
        }

        private static String readSourceId(JsonNode node) {
            JsonNode idNode = node.path("_id");
            if (idNode.has("$oid")) {
                return idNode.path("$oid").asText();
            }
            if (idNode.isTextual() || idNode.isNumber()) {
                return idNode.asText();
            }
            String stableText = node.path("name").asText("") + "\n" + node.path("desc").asText("");
            return "sha256-" + sha256(stableText).substring(0, 32);
        }

        private static String joinText(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "";
            }
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (JsonNode item : node) {
                    String value = item.asText("").trim();
                    if (!value.isBlank() && seen.add(value)) {
                        values.add(value);
                    }
                }
                return String.join("、", values);
            }
            return node.asText("").trim();
        }

        private static JsonNode copyMetadataValue(JsonNode node, int maxTextChars) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return MAPPER.nullNode();
            }
            if (node.isTextual()) {
                return MAPPER.getNodeFactory().textNode(abbreviate(node.asText(), maxTextChars));
            }
            if (node.isArray()) {
                ArrayNode array = MAPPER.createArrayNode();
                for (JsonNode item : node) {
                    array.add(copyMetadataValue(item, maxTextChars));
                }
                return array;
            }
            if (node.isObject()) {
                ObjectNode object = MAPPER.createObjectNode();
                node.fields().forEachRemaining(entry ->
                        object.set(entry.getKey(), copyMetadataValue(entry.getValue(), maxTextChars)));
                return object;
            }
            return node.deepCopy();
        }

        private static void enforceMetadataLimit(ObjectNode metadata, Config config) {
            if (jsonBytes(metadata) <= config.metadataMaxBytes()) {
                return;
            }

            metadata.put("_metadata_truncated", true);
            ArrayNode removedFields = metadata.putArray("_metadata_removed_fields");
            for (String fieldName : TRIMMABLE_METADATA_FIELDS) {
                if (metadata.has(fieldName)) {
                    metadata.remove(fieldName);
                    removedFields.add(fieldName);
                    if (jsonBytes(metadata) <= config.metadataMaxBytes()) {
                        return;
                    }
                }
            }

            List<String> remainingFields = new ArrayList<>();
            metadata.fieldNames().forEachRemaining(remainingFields::add);
            for (String fieldName : remainingFields) {
                if ("source_id".equals(fieldName)
                        || "source_file".equals(fieldName)
                        || "_metadata_truncated".equals(fieldName)
                        || "_metadata_removed_fields".equals(fieldName)) {
                    continue;
                }
                metadata.remove(fieldName);
                removedFields.add(fieldName);
                if (jsonBytes(metadata) <= config.metadataMaxBytes()) {
                    return;
                }
            }

            throw new IllegalStateException("metadata 仍然超过 Milvus JSON 字段限制：source_id="
                    + metadata.path("source_id").asText()
                    + ", bytes=" + jsonBytes(metadata)
                    + ", limit=" + config.metadataMaxBytes());
        }
    }

    private static final class OpenAiEmbeddingClient {
        private final Config config;
        private final HttpClient httpClient;

        private OpenAiEmbeddingClient(Config config) {
            this.config = config;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();
        }

        List<List<Float>> embed(List<String> inputs) throws Exception {
            if (config.embeddingApiKey().isBlank()) {
                throw new IllegalStateException("请设置 EMBEDDING_API_KEY 或 OPENAI_API_KEY。");
            }

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("model", config.embeddingModel());
            ArrayNode inputNode = payload.putArray("input");
            inputs.forEach(inputNode::add);

            JsonNode root = postJsonWithRetry(
                    config.embeddingBaseUrl() + "/embeddings",
                    payload,
                    Map.of("Authorization", "Bearer " + config.embeddingApiKey()),
                    "Embedding API"
            );

            List<List<Float>> result = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                List<Float> vector = new ArrayList<>();
                for (JsonNode value : item.path("embedding")) {
                    vector.add((float) value.asDouble());
                }
                result.add(vector);
            }
            return result;
        }

        private JsonNode postJsonWithRetry(String url, JsonNode payload, Map<String, String> headers, String label)
                throws Exception {
            Exception last = null;
            for (int attempt = 1; attempt <= config.maxRetries(); attempt++) {
                try {
                    return postJson(url, payload, headers, label);
                } catch (Exception ex) {
                    last = ex;
                    if (attempt == config.maxRetries()) {
                        break;
                    }
                    Thread.sleep(Duration.ofSeconds((long) attempt * config.retryBackoffSeconds()).toMillis());
                }
            }
            throw last;
        }

        private JsonNode postJson(String url, JsonNode payload, Map<String, String> headers, String label)
                throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.httpTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8));
            headers.forEach(builder::header);

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(label + " 调用失败，HTTP " + response.statusCode()
                        + "：" + response.body());
            }
            return MAPPER.readTree(response.body());
        }
    }

    private static final class MilvusRestClient {
        private final Config config;
        private final HttpClient httpClient;

        private MilvusRestClient(Config config) {
            this.config = config;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();
        }

        void ensureDatabase() throws Exception {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("dbName", config.milvusDatabase());
            JsonNode response = postMilvus("/v2/vectordb/databases/create", payload, true);
            assertMilvusSuccessOrAlreadyExists(response, "创建 Milvus database");
        }

        void ensureCollection() throws Exception {
            ObjectNode hasPayload = MAPPER.createObjectNode();
            hasPayload.put("dbName", config.milvusDatabase());
            hasPayload.put("collectionName", config.milvusCollection());
            JsonNode hasResponse = postMilvus("/v2/vectordb/collections/has", hasPayload, false);
            assertMilvusSuccess(hasResponse, "检查 Milvus collection");
            if (hasResponse.path("data").path("has").asBoolean(false)) {
                loadCollection();
                return;
            }

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("dbName", config.milvusDatabase());
            payload.put("collectionName", config.milvusCollection());

            ObjectNode schema = payload.putObject("schema");
            schema.put("autoID", false);
            schema.put("enableDynamicField", true);
            ArrayNode fields = schema.putArray("fields");
            addVarcharField(fields, "id", true, 128);
            addVarcharField(fields, "name", false, config.nameMaxLength());
            addVarcharField(fields, "text", false, config.textMaxLength());

            ObjectNode vector = fields.addObject();
            vector.put("fieldName", "vector");
            vector.put("dataType", "FloatVector");
            vector.putObject("elementTypeParams").put("dim", config.embeddingDimension());

            ObjectNode metadata = fields.addObject();
            metadata.put("fieldName", "metadata");
            metadata.put("dataType", "JSON");

            ArrayNode indexParams = payload.putArray("indexParams");
            ObjectNode index = indexParams.addObject();
            index.put("fieldName", "vector");
            index.put("indexName", "vector_index");
            index.put("metricType", "COSINE");
            index.putObject("params").put("index_type", "AUTOINDEX");

            assertMilvusSuccess(postMilvus("/v2/vectordb/collections/create", payload, false), "创建 Milvus collection");
            loadCollection();
        }

        void upsert(ArrayNode entities) throws Exception {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("dbName", config.milvusDatabase());
            payload.put("collectionName", config.milvusCollection());
            payload.set("data", entities);
            assertMilvusSuccess(postMilvus("/v2/vectordb/entities/upsert", payload, false), "写入 Milvus");
        }

        private void loadCollection() throws Exception {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("dbName", config.milvusDatabase());
            payload.put("collectionName", config.milvusCollection());
            JsonNode response = postMilvus("/v2/vectordb/collections/load", payload, true);
            assertMilvusSuccessOrAlreadyExists(response, "加载 Milvus collection");
        }

        private static void addVarcharField(ArrayNode fields, String name, boolean primary, int maxLength) {
            ObjectNode field = fields.addObject();
            field.put("fieldName", name);
            field.put("dataType", "VarChar");
            field.put("isPrimary", primary);
            field.putObject("elementTypeParams").put("max_length", maxLength);
        }

        private JsonNode postMilvus(String path, JsonNode payload, boolean allowFailureBody) throws Exception {
            String token = config.milvusToken();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.milvusUri() + path))
                    .timeout(Duration.ofSeconds(config.httpTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8));
            if (!token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Milvus REST 调用失败，HTTP " + response.statusCode()
                        + "：" + response.body());
            }
            JsonNode root = MAPPER.readTree(response.body());
            if (!allowFailureBody && root.path("code").asInt(0) != 0) {
                throw new IllegalStateException("Milvus REST 调用失败：" + response.body());
            }
            return root;
        }

        private static void assertMilvusSuccess(JsonNode root, String action) {
            if (root.path("code").asInt(0) != 0) {
                throw new IllegalStateException(action + "失败：" + root);
            }
        }

        private static void assertMilvusSuccessOrAlreadyExists(JsonNode root, String action) {
            int code = root.path("code").asInt(0);
            String message = root.path("message").asText("").toLowerCase(Locale.ROOT);
            if (code != 0 && !message.contains("already") && !message.contains("exist")) {
                throw new IllegalStateException(action + "失败：" + root);
            }
        }
    }

    private record Config(
            Path inputFile,
            String embeddingBaseUrl,
            String embeddingApiKey,
            String embeddingModel,
            int embeddingDimension,
            int embeddingBatchSize,
            int embeddingTextMaxChars,
            String milvusUri,
            String milvusToken,
            String milvusDatabase,
            String milvusCollection,
            int httpTimeoutSeconds,
            int maxRetries,
            int retryBackoffSeconds,
            int limit,
            boolean dryRun,
            int nameMaxLength,
            int textMaxLength,
            int metadataMaxBytes,
            int metadataTextMaxChars
    ) {
        static Config fromEnv() {
            return new Config(
                    Path.of(env("MEDICAL_DATA_INPUT", "medical.data.unified.json")),
                    stripTrailingSlash(envAny(List.of("EMBEDDING_BASE_URL", "OPENAI_BASE_URL", "OPENAI_API_BASE"),
                            "https://api.openai.com/v1")),
                    envAny(List.of("EMBEDDING_API_KEY", "OPENAI_API_KEY"), ""),
                    env("EMBEDDING_MODEL", "text-embedding-3-small"),
                    intEnv("EMBEDDING_DIMENSION", 1536),
                    intEnv("EMBEDDING_BATCH_SIZE", 16),
                    intEnv("EMBEDDING_TEXT_MAX_CHARS", 6000),
                    stripTrailingSlash(env("MILVUS_URI", "http://localhost:19530")),
                    env("MILVUS_TOKEN", "root:Milvus"),
                    env("MILVUS_DATABASE", "medical"),
                    env("MILVUS_COLLECTION", "data"),
                    intEnv("HTTP_TIMEOUT_SECONDS", 120),
                    intEnv("MAX_RETRIES", 3),
                    intEnv("RETRY_BACKOFF_SECONDS", 2),
                    intEnv("IMPORT_LIMIT", 0),
                    boolEnv("DRY_RUN", false),
                    intEnv("MILVUS_NAME_MAX_LENGTH", 512),
                    intEnv("MILVUS_TEXT_MAX_LENGTH", 8192),
                    intEnv("MILVUS_METADATA_MAX_BYTES", 60000),
                    intEnv("MILVUS_METADATA_TEXT_MAX_CHARS", 2000)
            );
        }

        private static String env(String key, String defaultValue) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }

        private static String envAny(List<String> keys, String defaultValue) {
            for (String key : keys) {
                String value = System.getenv(key);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return defaultValue;
        }

        private static int intEnv(String key, int defaultValue) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
        }

        private static boolean boolEnv(String key, boolean defaultValue) {
            String value = System.getenv(key);
            return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
        }

        private static String stripTrailingSlash(String value) {
            return value.replaceAll("/+$", "");
        }
    }

    private static final class ImportStats {
        private int read;
        private int skipped;
        private int upserted;

        int read() {
            return read;
        }

        int skipped() {
            return skipped;
        }

        int upserted() {
            return upserted;
        }
    }

    private static String abbreviate(String text, int maxLength) {
        String value = Objects.toString(text, "");
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static int jsonBytes(JsonNode node) {
        try {
            return MAPPER.writeValueAsBytes(node).length;
        } catch (IOException ex) {
            throw new IllegalStateException("无法计算 JSON 字节长度", ex);
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }
}
