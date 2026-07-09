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
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MongoDiseaseRepository {
    private final AiProperties properties;

    public MongoDiseaseRepository(AiProperties properties) {
        this.properties = properties;
    }

    public Optional<DiseaseKnowledge> findByNameExact(String diseaseName) {
        if (diseaseName == null || diseaseName.isBlank()) {
            return Optional.empty();
        }
        try (MongoClient mongoClient = MongoClients.create(properties.mongo().uri())) {
            MongoCollection<Document> collection = mongoClient
                    .getDatabase(properties.mongo().database())
                    .getCollection(properties.mongo().collection());
            Document doc = collection.find(new Document("name", normalizeName(diseaseName)))
                    .projection(Projections.include(projectionFields()))
                    .first();
            return Optional.ofNullable(doc).map(this::toKnowledge);
        } catch (RuntimeException ex) {
            return Optional.empty();
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
                String.valueOf(doc.getObjectId("_id")),
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
