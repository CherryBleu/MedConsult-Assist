package com.medconsult.ai.client;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoDiseaseRepositoryTest {

    @Test
    void countDocumentsShouldReturnCountAndReuseLazyClient() {
        try (MongoFixture fixture = MongoFixture.open()) {
            when(fixture.collection.countDocuments()).thenReturn(8807L);
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            assertEquals(8807L, repository.countDocuments());
            assertEquals(8807L, repository.countDocuments());

            fixture.mongoClients.verify(() -> MongoClients.create("mongodb://test"), times(1));
        }
    }

    @Test
    void countDocumentsShouldReturnMinusOneWhenMongoClientCannotBeCreated() {
        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            mongoClients.when(() -> MongoClients.create("mongodb://test"))
                    .thenThrow(new IllegalStateException("mongo unavailable"));
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            assertEquals(-1L, repository.countDocuments());
        }
    }

    @Test
    void findByNameExactShouldNormalizeDiseaseNameAndMapKnowledgeFields() {
        try (MongoFixture fixture = MongoFixture.open()) {
            Document doc = diseaseDocument("d-1", "Flu", "respiratory infection", List.of("fever", "cough"))
                    .append("cause", "virus")
                    .append("cure_department", List.of("Respiratory"))
                    .append("ignored_field", "ignored");
            stubFindFirst(fixture.find, doc);
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            Optional<DiseaseKnowledge> result = repository.findByNameExact("可能Flu");

            assertTrue(result.isPresent());
            DiseaseKnowledge knowledge = result.orElseThrow();
            assertEquals("d-1", knowledge.vectorId());
            assertEquals("DISEASE_JSON:Flu", knowledge.sourceId());
            assertEquals("Flu", knowledge.diseaseName());
            assertEquals("respiratory infection", knowledge.desc());
            assertEquals(List.of("fever", "cough"), knowledge.symptoms());
            assertEquals("virus", knowledge.metadata().get("cause"));
            assertEquals(List.of("Respiratory"), knowledge.metadata().get("cure_department"));
            assertFalse(knowledge.metadata().containsKey("ignored_field"));
            assertEquals("name", knowledge.fieldName());
            assertEquals(1.0, knowledge.score());
            assertEquals(MatchSource.MONGODB_NAME_EXACT, knowledge.source());

            ArgumentCaptor<Document> queryCaptor = ArgumentCaptor.forClass(Document.class);
            verify(fixture.collection).find(queryCaptor.capture());
            assertEquals("Flu", queryCaptor.getValue().getString("name"));
        }
    }

    @Test
    void findByNameExactShouldNotOpenMongoForBlankName() {
        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            assertTrue(repository.findByNameExact(" ").isEmpty());
            mongoClients.verifyNoInteractions();
        }
    }

    @Test
    void findByNameExactShouldDegradeToEmptyWhenMongoFails() {
        try (MongoFixture fixture = MongoFixture.open()) {
            when(fixture.collection.find(any(Document.class))).thenThrow(new IllegalStateException("find failed"));
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            assertTrue(repository.findByNameExact("Flu").isEmpty());
        }
    }

    @Test
    void findBySymptomShouldQueryArrayWithInAndClampLimit() {
        try (MongoFixture fixture = MongoFixture.open()) {
            Document doc = diseaseDocument("d-2", "Asthma", "airway inflammation", "wheeze")
                    .append("check", "spirometry");
            stubInto(fixture.find, List.of(doc));
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            List<DiseaseKnowledge> result = repository.findBySymptom(List.of("wheeze", "cough"), 0);

            assertEquals(1, result.size());
            DiseaseKnowledge knowledge = result.getFirst();
            assertEquals("Asthma", knowledge.diseaseName());
            assertEquals(List.of("wheeze"), knowledge.symptoms());
            assertEquals("spirometry", knowledge.metadata().get("check"));

            ArgumentCaptor<Document> queryCaptor = ArgumentCaptor.forClass(Document.class);
            verify(fixture.collection).find(queryCaptor.capture());
            Document inQuery = queryCaptor.getValue().get("symptom", Document.class);
            assertEquals(List.of("wheeze", "cough"), inQuery.get("$in"));
            verify(fixture.find).limit(1);
        }
    }

    @Test
    void findBySymptomShouldReturnEmptyForMissingSymptomsOrMongoFailure() {
        try (MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class)) {
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            assertTrue(repository.findBySymptom(List.of(), 5).isEmpty());
            mongoClients.verifyNoInteractions();
        }

        try (MongoFixture fixture = MongoFixture.open()) {
            when(fixture.collection.find(any(Document.class))).thenThrow(new IllegalStateException("find failed"));
            MongoDiseaseRepository repository = new MongoDiseaseRepository(aiProperties());

            assertTrue(repository.findBySymptom(List.of("fever"), 5).isEmpty());
        }
    }

    private static Document diseaseDocument(String id, String name, String desc, Object symptoms) {
        return new Document("_id", id)
                .append("name", name)
                .append("desc", desc)
                .append("symptom", symptoms);
    }

    private static void stubFindFirst(FindIterable<Document> find, Document doc) {
        when(find.projection(any())).thenReturn(find);
        when(find.first()).thenReturn(doc);
    }

    private static void stubInto(FindIterable<Document> find, List<Document> docs) {
        when(find.projection(any())).thenReturn(find);
        when(find.limit(anyInt())).thenReturn(find);
        when(find.into(any())).thenAnswer(invocation -> {
            List<Document> target = invocation.getArgument(0);
            target.addAll(docs);
            return target;
        });
    }

    private static AiProperties aiProperties() {
        return new AiProperties(
                null,
                null,
                new AiProperties.MongoProperties("mongodb://test", "medical", "diseases"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private static final class MongoFixture implements AutoCloseable {
        private final MockedStatic<MongoClients> mongoClients;
        private final MongoCollection<Document> collection;
        private final FindIterable<Document> find;

        private MongoFixture(MockedStatic<MongoClients> mongoClients,
                             MongoCollection<Document> collection,
                             FindIterable<Document> find) {
            this.mongoClients = mongoClients;
            this.collection = collection;
            this.find = find;
        }

        static MongoFixture open() {
            MockedStatic<MongoClients> mongoClients = mockStatic(MongoClients.class);
            MongoClient client = mock(MongoClient.class);
            MongoDatabase database = mock(MongoDatabase.class);
            MongoCollection<Document> collection = mock(MongoCollection.class);
            FindIterable<Document> find = mock(FindIterable.class);

            mongoClients.when(() -> MongoClients.create("mongodb://test")).thenReturn(client);
            when(client.getDatabase("medical")).thenReturn(database);
            when(database.getCollection("diseases")).thenReturn(collection);
            when(collection.find(any(Document.class))).thenReturn(find);

            return new MongoFixture(mongoClients, collection, find);
        }

        @Override
        public void close() {
            mongoClients.close();
        }
    }
}
