package com.medconsult.ai.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DiseaseKnowledgeModels {
    private DiseaseKnowledgeModels() {
    }

    public record DiseaseIntent(
            List<DiseaseCandidate> candidates,
            MetadataQuery metadataQuery
    ) {
        public DiseaseCandidate toSearchInfo() {
            String diseaseNames = candidates.stream()
                    .map(DiseaseCandidate::diseaseName)
                    .filter(name -> !DiseaseCandidate.isPlaceholder(name))
                    .distinct()
                    .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
            List<String> symptoms = candidates.stream()
                    .flatMap(candidate -> candidate.symptoms().stream())
                    .distinct()
                    .toList();
            String description = candidates.stream()
                    .map(DiseaseCandidate::description)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
            return new DiseaseCandidate(diseaseNames, symptoms, description);
        }

        public String searchText() {
            return toSearchInfo().searchText();
        }
    }

    public record DiseaseCandidate(
            String diseaseName,
            List<String> symptoms,
            String description
    ) {
        public DiseaseCandidate {
            symptoms = symptoms == null ? List.of() : symptoms;
        }

        public boolean isPlaceholderDiseaseName() {
            return isPlaceholder(diseaseName);
        }

        public String symptomText() {
            return String.join(" ", symptoms);
        }

        public String searchText() {
            return String.join(" ",
                    Objects.toString(diseaseName, ""),
                    symptomText(),
                    Objects.toString(description, "")
            ).trim();
        }

        static boolean isPlaceholder(String name) {
            String value = Objects.toString(name, "").trim();
            return value.isBlank() || value.contains("待鉴别") || value.equalsIgnoreCase("unknown");
        }
    }

    public record MetadataQuery(
            List<String> requestedFields,
            Map<String, List<String>> filters
    ) {
        public MetadataQuery {
            requestedFields = requestedFields == null ? List.of() : requestedFields;
            filters = filters == null ? Map.of() : filters;
        }
    }

    public record DiseaseKnowledge(
            String vectorId,
            String sourceId,
            String diseaseName,
            String desc,
            List<String> symptoms,
            Map<String, Object> metadata,
            String fieldName,
            String chunkText,
            double score,
            MatchSource source
    ) {
    }

    public enum MatchSource {
        REDIS_CACHE,
        MONGODB_NAME_EXACT,
        MILVUS_SEMANTIC
    }

    public record RiskAssessment(
            String riskLevel,
            boolean emergencyAdvice,
            List<String> reasons
    ) {
    }
}
