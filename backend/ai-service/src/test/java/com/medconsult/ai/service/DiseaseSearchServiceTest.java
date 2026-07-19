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
    void extractIntentShouldUseLocalRulesWithoutCallingGenerativeLlm() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);

        DiseaseIntent intent = service.extractIntent("cough for three days");

        assertEquals(1, intent.candidates().size());
        assertTrue(intent.candidates().getFirst().isPlaceholderDiseaseName());
        assertEquals(List.of("cough for three days"), intent.candidates().getFirst().symptoms());
        assertTrue(intent.metadataQuery().requestedFields().isEmpty());
        verify(llmClient, times(0)).chatJson(anyString(), anyString());
        verify(llmClient, times(0)).embedOne(anyString());
    }

    @Test
    void extractIntentShouldMapFieldKeywordsSymptomsAndExplicitDiseaseNames() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);

        DiseaseIntent intent = service.extractIntent(
                "我想咨询肺炎。孩子咳嗽像鸡叫，挂号，医保，怎么治疗，费用，化验，用药，饮食"
        );

        assertEquals("肺炎", intent.candidates().getFirst().diseaseName());
        assertTrue(intent.candidates().getLast().isPlaceholderDiseaseName());
        assertTrue(intent.candidates().getLast().symptoms().contains("咳嗽"));
        assertTrue(intent.metadataQuery().requestedFields().containsAll(List.of(
                "cure_department",
                "yibao_status",
                "cure_way",
                "cure_lasttime",
                "cost_money",
                "check",
                "common_drug",
                "recommand_drug",
                "do_eat",
                "not_eat",
                "recommand_eat"
        )));
        assertEquals(List.of("儿科"), intent.metadataQuery().filters().get("cure_department"));
    }

    @Test
    void extractIntentShouldInferHighSpecificityDiseaseHintsFromSymptoms() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);

        DiseaseIntent childCough = service.extractIntent("孩子咳嗽像鸡叫，咳后呕吐，应该挂什么科");
        DiseaseIntent chestPain = service.extractIntent("持续胸痛伴呼吸困难和大汗");

        assertEquals("百日咳", childCough.candidates().getFirst().diseaseName());
        assertTrue(childCough.toSearchInfo().symptoms().contains("咳后呕吐"));
        assertEquals(List.of("儿科"), childCough.metadataQuery().filters().get("cure_department"));

        assertTrue(chestPain.candidates().stream()
                .map(DiseaseCandidate::diseaseName)
                .anyMatch(name -> name.equals("急性心肌梗死")));
        assertTrue(chestPain.candidates().stream()
                .map(DiseaseCandidate::diseaseName)
                .anyMatch(name -> name.equals("急性冠脉综合征")));
        assertTrue(chestPain.toSearchInfo().symptoms().contains("胸痛"));
        assertTrue(chestPain.toSearchInfo().symptoms().contains("大汗"));
    }

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
    void namedCandidateSearchShouldUseCachedKnowledgeWithoutRepositoryLookup() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("Disease-1", List.of("cough"), "cached candidate")),
                new MetadataQuery(List.of("check"), Map.of())
        );
        DiseaseKnowledge cached = knowledge("redis-1", "Disease-1", 0.99, MatchSource.REDIS_CACHE);
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.of(cached));

        List<DiseaseKnowledge> results = service.search("cough", intent, 1);

        assertEquals(List.of("Disease-1"), results.stream().map(DiseaseKnowledge::diseaseName).toList());
        assertEquals(MatchSource.REDIS_CACHE, results.getFirst().source());
        verify(mongoDiseaseRepository, times(0)).findByNameExact(anyString());
        verify(llmClient, times(0)).embedOne(anyString());
        verify(milvusRestClient, times(0)).search(anyList(), eq(1));
    }

    @Test
    void namedCandidateSearchShouldUseMilvusWhenCacheAndMongoMiss() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("Disease-1", List.of("cough"), "semantic candidate")),
                new MetadataQuery(List.of(), Map.of())
        );
        DiseaseKnowledge semantic = knowledge("milvus-1", "Disease-1", 0.91, MatchSource.MILVUS_SEMANTIC);
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mongoDiseaseRepository.findByNameExact("Disease-1")).thenReturn(Optional.empty());
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f)));
        when(milvusRestClient.search(anyList(), eq(1))).thenReturn(List.of(semantic));

        List<DiseaseKnowledge> results = service.search("cough", intent, 1);

        assertEquals(List.of("Disease-1"), results.stream().map(DiseaseKnowledge::diseaseName).toList());
        assertEquals(MatchSource.MILVUS_SEMANTIC, results.getFirst().source());
        verify(cacheService).put(anyString(), any(), any(), eq(semantic));
    }

    @Test
    void namedCandidateSearchShouldSkipFailedCandidateAndReturnSuccessfulCandidates() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(
                        new DiseaseCandidate("Disease-1", List.of("cough"), "broken downstream"),
                        new DiseaseCandidate("Disease-2", List.of("cough"), "healthy downstream")
                ),
                new MetadataQuery(List.of(), Map.of())
        );
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mongoDiseaseRepository.findByNameExact("Disease-1"))
                .thenThrow(new IllegalStateException("mongo unavailable"));
        when(mongoDiseaseRepository.findByNameExact("Disease-2"))
                .thenReturn(Optional.of(knowledge("mongo-2", "Disease-2", 1.0, MatchSource.MONGODB_NAME_EXACT)));

        List<DiseaseKnowledge> results = service.search("cough", intent, 2);

        assertEquals(List.of("Disease-2"), results.stream().map(DiseaseKnowledge::diseaseName).toList());
    }

    @Test
    void symptomSearchShouldSupplementSpecificSemanticEvidenceEvenWhenMongoHasEnoughGenericMatches() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("unknown",
                        List.of("咳嗽", "儿童患者", "鸡鸣样吸气声", "咳后呕吐"), "specific symptom-only input")),
                new MetadataQuery(List.of(), Map.of())
        );
        when(mongoDiseaseRepository.findBySymptom(List.of("咳嗽", "儿童患者", "鸡鸣样吸气声", "咳后呕吐"), 5)).thenReturn(List.of(
                knowledge("mongo-1", "肺炎", 1.0, MatchSource.MONGODB_NAME_EXACT),
                knowledge("mongo-2", "禽流感", 1.0, MatchSource.MONGODB_NAME_EXACT),
                knowledge("mongo-3", "气管肿瘤", 1.0, MatchSource.MONGODB_NAME_EXACT),
                knowledge("mongo-4", "哮喘", 1.0, MatchSource.MONGODB_NAME_EXACT),
                knowledge("mongo-5", "粒细胞缺乏症", 1.0, MatchSource.MONGODB_NAME_EXACT)
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(5))).thenReturn(List.of(
                knowledge("milvus-pertussis", "百日咳", 0.97, MatchSource.MILVUS_SEMANTIC,
                        "symptom,cure_department", "疾病名称：百日咳\n症状：鸡鸣样吸气声、咳后呕吐")
        ));

        List<DiseaseKnowledge> results = service.search("孩子咳嗽像鸡叫，咳后呕吐，应该挂什么科", intent, 5);

        assertTrue(results.stream().map(DiseaseKnowledge::diseaseName).toList().contains("百日咳"));
        verify(llmClient).embedOne(anyString());
        verify(milvusRestClient).search(anyList(), eq(5));
    }

    @Test
    void highRiskCardiacSymptomsShouldUseRuleHintsWithDepartmentEvidence() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);

        DiseaseIntent intent = service.extractIntent("持续胸痛伴呼吸困难和大汗");
        when(cacheService.get(anyString(), any(), any())).thenReturn(Optional.empty());
        when(mongoDiseaseRepository.findByNameExact("急性心肌梗死"))
                .thenReturn(Optional.of(knowledgeWithDepartment("mongo-ami", "急性心肌梗死", "心胸外科")));
        when(mongoDiseaseRepository.findByNameExact("急性冠脉综合征"))
                .thenReturn(Optional.of(knowledgeWithDepartment("mongo-acs", "急性冠脉综合征", "心内科")));
        when(mongoDiseaseRepository.findBySymptom(anyList(), eq(5))).thenReturn(List.of(
                knowledge("mongo-generic", "食管源性胸痛", 1.0, MatchSource.MONGODB_NAME_EXACT)
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(4))).thenReturn(List.of());

        List<DiseaseKnowledge> results = service.search("持续胸痛伴呼吸困难和大汗", intent, 5);

        assertEquals(List.of("急性心肌梗死", "急性冠脉综合征", "食管源性胸痛"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
        assertTrue(results.stream()
                .filter(item -> item.diseaseName().equals("急性心肌梗死"))
                .flatMap(item -> item.metadata().keySet().stream())
                .anyMatch("cure_department"::equals));
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
    void symptomSearchShouldPreferEvidenceMatchingOriginalComplaintWhenTruncating() {
        OpenAiCompatibleClient llmClient = mock(OpenAiCompatibleClient.class);
        MongoDiseaseRepository mongoDiseaseRepository = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvusRestClient = mock(MilvusRestClient.class);
        DiseaseCacheService cacheService = mock(DiseaseCacheService.class);
        DiseaseSearchService service = new DiseaseSearchService(
                llmClient, mongoDiseaseRepository, milvusRestClient, cacheService);
        DiseaseIntent intent = new DiseaseIntent(
                List.of(new DiseaseCandidate("待鉴别", List.of("咳嗽", "胸闷"), "纯症状输入")),
                new MetadataQuery(List.of(), Map.of())
        );
        when(mongoDiseaseRepository.findBySymptom(List.of("咳嗽", "胸闷"), 2)).thenReturn(List.of(
                knowledge("mongo-cold", "普通感冒", 1.0, MatchSource.MONGODB_NAME_EXACT,
                        "symptom", "症状：咳嗽、流涕")
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(2))).thenReturn(List.of(
                knowledge("milvus-rhinitis", "过敏性鼻炎", 0.95, MatchSource.MILVUS_SEMANTIC,
                        "symptom", "症状：打喷嚏、流涕、咳嗽"),
                knowledge("milvus-bronchitis", "急性支气管炎", 0.90, MatchSource.MILVUS_SEMANTIC,
                        "symptom", "症状：咳嗽、咳痰、胸闷")
        ));

        List<DiseaseKnowledge> results = service.search("咳嗽伴咳痰和胸闷", intent, 2);

        assertEquals(2, results.size());
        assertEquals("急性支气管炎", results.getFirst().diseaseName());
    }

    @Test
    void symptomSearchShouldKeepExistingRankingForSingleGenericKeywordWhenTruncating() {
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
                knowledge("mongo-cold", "普通感冒", 0.50, MatchSource.MONGODB_NAME_EXACT,
                        "symptom", "症状：咳嗽、鼻塞")
        ));
        when(llmClient.embedOne(anyString())).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));
        when(milvusRestClient.search(anyList(), eq(2))).thenReturn(List.of(
                knowledge("milvus-pneumonia", "肺炎", 0.95, MatchSource.MILVUS_SEMANTIC,
                        "symptom", "症状：咳嗽、发热"),
                knowledge("milvus-bronchitis", "急性支气管炎", 0.94, MatchSource.MILVUS_SEMANTIC,
                        "symptom", "症状：咳嗽、咳痰")
        ));

        List<DiseaseKnowledge> results = service.search("咳嗽三天", intent, 2);

        assertEquals(List.of("普通感冒", "肺炎"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
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

    private static DiseaseKnowledge knowledgeWithDepartment(String id, String name, String department) {
        return new DiseaseKnowledge(
                id,
                "DISEASE_JSON:" + name,
                name,
                name + "描述",
                List.of("胸痛", "呼吸困难"),
                Map.of("cure_department", List.of(department)),
                "name",
                "疾病名称：" + name + "\n症状：胸痛、呼吸困难\n疾病描述：" + name + "描述",
                1.0,
                MatchSource.MONGODB_NAME_EXACT
        );
    }
}
