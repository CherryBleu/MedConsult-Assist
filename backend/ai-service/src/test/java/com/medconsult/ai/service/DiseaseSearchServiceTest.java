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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiseaseSearchServiceTest {

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
        when(milvusRestClient.search(anyList(), eq(3))).thenReturn(List.of(
                knowledge("milvus-dup", "肺炎", 0.92, MatchSource.MILVUS_SEMANTIC),
                knowledge("milvus-3", "百日咳", 0.88, MatchSource.MILVUS_SEMANTIC)
        ));

        List<DiseaseKnowledge> results = service.search("孩子咳嗽像鸡叫", intent, 3);

        assertEquals(List.of("急性支气管炎", "肺炎", "百日咳"),
                results.stream().map(DiseaseKnowledge::diseaseName).toList());
        verify(llmClient).embedOne(anyString());
        verify(milvusRestClient).search(anyList(), eq(3));
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
}
