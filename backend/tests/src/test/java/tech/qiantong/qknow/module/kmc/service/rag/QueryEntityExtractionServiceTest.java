package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryEntityExtractionServiceTest {

    @Test
    void parsesRawJsonEntities() throws Exception {
        QueryEntityExtractionService service = createService();

        List<String> entities = service.parse("{\"entities\":[\"A公司\",\"B公司\",\"A公司\"]}");

        assertEquals(List.of("A公司", "B公司"), entities);
    }

    @Test
    void parsesFencedJsonEntities() throws Exception {
        QueryEntityExtractionService service = createService();

        List<String> entities = service.parse("```json\n{\"entities\":[\"qKnow\",\"CRAG\"]}\n```");

        assertEquals(List.of("qKnow", "CRAG"), entities);
    }

    @Test
    void invalidJsonReturnsEmptyEntities() throws Exception {
        QueryEntityExtractionService service = createService();

        assertEquals(List.of(), service.parse("not json"));
    }

    private QueryEntityExtractionService createService() throws Exception {
        Class<?> chatModelServiceType = Class.forName("tech.qiantong.qknow.ai.service.IChatModelService");
        return (QueryEntityExtractionService) QueryEntityExtractionService.class
                .getConstructor(chatModelServiceType, QueryEntityExtractionService.QueryEntityConfig.class)
                .newInstance(null, new QueryEntityExtractionService.QueryEntityConfig());
    }
}
