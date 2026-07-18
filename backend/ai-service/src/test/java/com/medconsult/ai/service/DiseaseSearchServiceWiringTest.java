package com.medconsult.ai.service;

import com.medconsult.ai.client.MilvusRestClient;
import com.medconsult.ai.client.MongoDiseaseRepository;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiseaseSearchServiceWiringTest {

    @Test
    void springSelectsTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OpenAiCompatibleClient.class, () -> mock(OpenAiCompatibleClient.class));
            context.registerBean(MongoDiseaseRepository.class, () -> mock(MongoDiseaseRepository.class));
            context.registerBean(MilvusRestClient.class, () -> mock(MilvusRestClient.class));
            context.registerBean(DiseaseCacheService.class, () -> mock(DiseaseCacheService.class));
            context.register(DiseaseSearchService.class);

            context.refresh();

            assertThat(context.getBean(DiseaseSearchService.class)).isNotNull();
        }
    }
}
