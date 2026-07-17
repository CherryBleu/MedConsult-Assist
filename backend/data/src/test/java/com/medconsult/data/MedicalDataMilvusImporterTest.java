package com.medconsult.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
