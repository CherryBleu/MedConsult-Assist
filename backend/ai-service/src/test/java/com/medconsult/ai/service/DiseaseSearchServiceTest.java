package com.medconsult.ai.service;

import com.medconsult.ai.client.MilvusRestClient;
import com.medconsult.ai.client.MongoDiseaseRepository;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseIntent;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiseaseSearchServiceTest {

    @Test
    void namedCandidateSearchShouldReturnFiveResultsWhenTopKIsFive() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = namedIntent(5);
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.empty());
        for (int i = 1; i <= 5; i++) {
            when(mongoDiseaseRepository.findByNameExact("Disease-" + i))
                    .thenReturn(Optional.of(knowledge("mongo-" + i, "Disease-" + i, 1.0, MatchSource.MONGODB_NAME_EXACT)));
        }

        List<DiseaseKnowledge> results = service.search("symptom text", intent, 5);

        assertEquals(List.of("Disease-1", "Disease-2", "Disease-3", "Disease-4", "Disease-5"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
    }

    @Test
    void namedCandidateSearchShouldCapExcessiveTopKToTen() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = namedIntent(12);
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.empty());
        for (int i = 1; i <= 12; i++) {
            when(mongoDiseaseRepository.findByNameExact("Disease-" + i))
                    .thenReturn(Optional.of(knowledge("mongo-" + i, "Disease-" + i, 1.0, MatchSource.MONGODB_NAME_EXACT)));
        }

        List<DiseaseKnowledge> results = service.search("symptom text", intent, 50);

        assertEquals(10, results.size());
        assertEquals("Disease-1", results.getFirst().diseaseName());
        assertEquals("Disease-10", results.getLast().diseaseName());
        verify(mongoDiseaseRepository, times(10)).findByNameExact(anyString());
    }

    @Test
    void symptomSearchShouldSupplementMongoShortageWithSemanticMatchesUntilFive() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("unknown", List.of("cough"), "symptom-only input")),
                new MetadataQuery(List.of(), Map.of())
        );
        when(mongoDiseaseRepository.findBySymptom(List.of("cough"), 5)).thenReturn(List.of(
                knowledge("mongo-1", "Disease-1", 1.0, MatchSource.MONGODB_NAME_EXACT),
                knowledge("mongo-2", "Disease-2", 1.0, MatchSource.MONGODB_NAME_EXACT)
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(5))).thenReturn(List.of(
                knowledge("milvus-dup", "Disease-2", 0.95, MatchSource.MILVUS_SEMANTIC),
                knowledge("milvus-3", "Disease-3", 0.93, MatchSource.MILVUS_SEMANTIC),
                knowledge("milvus-4", "Disease-4", 0.91, MatchSource.MILVUS_SEMANTIC),
                knowledge("milvus-5", "Disease-5", 0.89, MatchSource.MILVUS_SEMANTIC)
        ));

        List<DiseaseKnowledge> results = service.search("cough for three days", intent, 5);

        assertEquals(List.of("Disease-1", "Disease-2", "Disease-3", "Disease-4", "Disease-5"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
        verify(mongoDiseaseRepository).findBySymptom(List.of("cough"), 5);
        verify(milvusRestClient).search(anyList(), eq(5));
    }

    @Test
    void symptomSearchShouldMergeMongoAndSemanticMatchesUntilTopK() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("待鉴别", List.of("咳嗽"), "纯症状输入")),
                new MetadataQuery(List.of(), Map.of())
        );
        when(mongoDiseaseRepository.findBySymptom(List.of("咳嗽"), 3)).thenReturn(List.of(
                knowledge("mongo-1", "急性支气管炎", 1.0, MatchSource.MONGODB_NAME_EXACT),
                knowledge("mongo-2", "肺炎", 1.0, MatchSource.MONGODB_NAME_EXACT)
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(2))).thenReturn(List.of(
                knowledge("milvus-dup", "肺炎", 0.92, MatchSource.MILVUS_SEMANTIC),
                knowledge("milvus-3", "百日咳", 0.88, MatchSource.MILVUS_SEMANTIC)
        ));

        List<DiseaseKnowledge> results = service.search("孩子咳嗽像鸡叫", intent, 3);

        assertEquals(List.of("急性支气管炎", "肺炎", "百日咳"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
        verify(llmClient).embedOne(anyString());
        verify(milvusRestClient).search(anyList(), eq(2));
    }

    @Test
    void symptomSearchShouldAggregateDuplicateDiseaseEvidenceInsteadOfDroppingFields() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("待鉴别", List.of("咳嗽"), "纯症状输入")),
                new MetadataQuery(List.of(), Map.of())
        );
        when(mongoDiseaseRepository.findBySymptom(List.of("咳嗽"), 2)).thenReturn(List.of(
                knowledge("mongo-1", "肺炎", 0.90, MatchSource.MONGODB_NAME_EXACT,
                        "symptom", "症状：咳嗽、咳痰")
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(2))).thenReturn(List.of(
                knowledge("milvus-dup", "肺炎", 0.95, MatchSource.MILVUS_SEMANTIC,
                        "cause", "病因：感染、免疫力下降"),
                knowledge("milvus-2", "急性支气管炎", 0.88, MatchSource.MILVUS_SEMANTIC,
                        "symptom", "症状：咳嗽伴气促")
        ));

        List<DiseaseKnowledge> results = service.search("咳嗽三天", intent, 2);

        assertEquals(List.of("肺炎", "急性支气管炎"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
        DiseaseKnowledge pneumonia = results.getFirst();
        assertTrue(List.of(pneumonia.fieldName().split(",")).containsAll(List.of("symptom", "cause")));
        assertTrue(pneumonia.chunkText().contains("症状：咳嗽、咳痰"));
        assertTrue(pneumonia.chunkText().contains("病因：感染、免疫力下降"));
        assertEquals(0.95, pneumonia.score(), 0.001);
    }

    @Test
    void namedCandidateSearchShouldRespectRetrievalBudgetAndReturnPartialResults() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService, 50);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(
                        new DiseaseCandidate("快速疾病", List.of("咳嗽"), "快速返回"),
                        new DiseaseCandidate("慢速疾病", List.of("咳嗽"), "下游阻塞")
                ),
                new MetadataQuery(List.of(), Map.of())
        );
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mongoDiseaseRepository.findByNameExact("快速疾病"))
                .thenReturn(Optional.of(knowledge("mongo-fast", "快速疾病", 1.0, MatchSource.MONGODB_NAME_EXACT)));
        when(mongoDiseaseRepository.findByNameExact("慢速疾病")).thenAnswer(invocation -> {
            Thread.sleep(500);
            return Optional.of(knowledge("mongo-slow", "慢速疾病", 1.0, MatchSource.MONGODB_NAME_EXACT));
        });

        List<DiseaseKnowledge> results = assertTimeoutPreemptively(Duration.ofMillis(500),
                () -> service.search("咳嗽", intent, 2));

        assertEquals(List.of("快速疾病"), results.stream().map(DiseaseKnowledge::diseaseName).toList());
    }

    private static DiseaseIntent namedIntent(int count) {
        List<DiseaseCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new DiseaseCandidate("Disease-" + index, List.of("symptom"), "candidate " + index))
                .toList();
        return new DiseaseIntent(candidates, new MetadataQuery(List.of(), Map.of()));
    }

    private static DiseaseKnowledge knowledge(String id, String name, double score, MatchSource source) {
        return new DiseaseKnowledge(
                id,
                "DISEASE_JSON:" + name,
                name,
                name + "描述",
                List.of("咳嗽"),
                Map.of("cure_department", List.of("呼吸内科")),
                "name",
                "疾病名称：" + name + "\n症状：咳嗽\n疾病描述：" + name + "描述",
                score,
                source
        );
    }

    private static DiseaseKnowledge knowledge(String id, String name, double score, MatchSource source,
                                              String fieldName, String chunkText) {
        return new DiseaseKnowledge(
                id,
                "DISEASE_JSON:" + name,
                name,
                name + "描述",
                List.of("咳嗽"),
                Map.of("cure_department", List.of("呼吸内科")),
                fieldName,
                chunkText,
                score,
                source
        );
    }
}
