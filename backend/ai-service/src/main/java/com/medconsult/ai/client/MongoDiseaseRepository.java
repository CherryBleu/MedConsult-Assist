package com.medconsult.ai.client;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.KnowledgeFields;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MongoDiseaseRepository {
    private static final Logger log = LoggerFactory.getLogger(MongoDiseaseRepository.class);

    private final AiProperties properties;
    /**
     * 复用单一 MongoClient（驱动内部自带连接池，线程安全）。
     * 之前每次查询都 MongoClients.create + try-with-resources 关闭，频繁建连开销大且易耗尽连接数。
     * 懒初始化：首次使用时创建，之后复用；URI 配置变化在重启后生效（与原行为一致）。
     */
    private volatile MongoClient mongoClient;

    public MongoDiseaseRepository(AiProperties properties) {
        this.properties = properties;
    }

    private MongoClient mongoClient() {
        MongoClient local = mongoClient;
        if (local == null) {
            synchronized (this) {
                local = mongoClient;
                if (local == null) {
                    local = MongoClients.create(properties.mongo().uri());
                    mongoClient = local;
                }
            }
        }
        return local;
    }

    public long countDocuments() {
        try {
            MongoClient client = mongoClient();
            MongoCollection<Document> collection = client
                    .getDatabase(properties.mongo().database())
                    .getCollection(properties.mongo().collection());
            return collection.countDocuments();
        } catch (Exception ex) {
            log.warn("MongoDB disease count failed: {}", ex.getMessage());
            return -1L;
        }
    }

    public Optional<DiseaseKnowledge> findByNameExact(String diseaseName) {
        if (diseaseName == null || diseaseName.isBlank()) {
            return Optional.empty();
        }
        try {
            MongoClient client = mongoClient();
            MongoCollection<Document> collection = client
                    .getDatabase(properties.mongo().database())
                    .getCollection(properties.mongo().collection());
            Document doc = collection.find(new Document("name", normalizeName(diseaseName)))
                    .projection(Projections.include(projectionFields()))
                    .first();
            return Optional.ofNullable(doc).map(this::toKnowledge);
        } catch (Exception ex) {
            // 连接异常 / 驱动不兼容（检查异常）也应降级为 miss，
            // 让 DiseaseSearchService 继续走 Milvus 兜底，而不是整个链路 500。
            log.warn("MongoDB disease exact match failed, degrade to miss: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按症状关键词查询疾病（纯规则兜底路径）。
     *
     * <p>用于纯症状输入（如"咳嗽""胸闷"）的检索兜底：当用户未明确提及疾病名、
     * 候选全是"待鉴别"占位符时，用抽取到的症状关键词在 Mongo 的 {@code symptom}
     * 数组字段上做 {@code $in} 匹配（MongoDB 自动展开数组元素），召回含有这些症状的疾病。
     *
     * <p>这是 Embedding/Milvus 不可用时的降级路径——即使本地 Embedding 容器或
     * Milvus 未启动，只要 Mongo 疾病集合已导入，也能基于症状给出基本建议，
     * 避免纯症状输入永远落到"检索未获得足够依据"的空提示。
     *
     * @param symptoms 症状关键词列表（如 ["咳嗽","呼吸困难"]）
     * @param limit     最多返回条数
     * @return 命中的疾病知识列表；查询异常或 symptoms 为空时返回空列表
     */
    public List<DiseaseKnowledge> findBySymptom(List<String> symptoms, int limit) {
        if (symptoms == null || symptoms.isEmpty()) {
            return List.of();
        }
        try {
            MongoClient client = mongoClient();
            MongoCollection<Document> collection = client
                    .getDatabase(properties.mongo().database())
                    .getCollection(properties.mongo().collection());
            // symptom 是数组字段，$in 会自动展开匹配数组元素
            List<Document> docs = collection
                    .find(new Document("symptom", new Document("$in", symptoms)))
                    .projection(Projections.include(projectionFields()))
                    .limit(Math.max(1, limit))
                    .into(new ArrayList<>());
            return docs.stream().map(this::toKnowledge).toList();
        } catch (Exception ex) {
            log.warn("MongoDB disease symptom match failed, degrade to empty: {}", ex.getMessage());
            return List.of();
        }
    }

    private DiseaseKnowledge toKnowledge(Document doc) {
        String name = doc.getString("name");
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String field : KnowledgeFields.STANDARD_METADATA_FIELDS) {
            if (doc.containsKey(field)) {
                metadata.put(field, doc.get(field));
            }
        }
        List<String> symptoms = toStringList(doc.get("symptom"));
        String desc = doc.getString("desc");
        String chunk = "疾病名称：" + name + "\n症状：" + String.join("、", symptoms) + "\n疾病描述：" + desc;
        return new DiseaseKnowledge(
                // _id 类型因导入方式而异：mongoimport 保留为 String、Spring Data 写入为 ObjectId。
                // 用 doc.get 读取原始值再 String.valueOf，避免 getObjectId 对 String 抛 ClassCastException
                // （否则 findBySymptom 整条降级为空，RAG 永远返回兜底话术）。
                String.valueOf(doc.get("_id")),
                "DISEASE_JSON:" + name,
                name,
                desc,
                symptoms,
                metadata,
                "name",
                chunk,
                1.0,
                MatchSource.MONGODB_NAME_EXACT
        );
    }

    private static List<String> projectionFields() {
        List<String> fields = new ArrayList<>(List.of("name", "desc", "symptom"));
        fields.addAll(KnowledgeFields.STANDARD_METADATA_FIELDS);
        return fields;
    }

    private static String normalizeName(String diseaseName) {
        return diseaseName
                .replace("待鉴别", "")
                .replace("可能", "")
                .trim();
    }

    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of(String.valueOf(value));
    }
}
