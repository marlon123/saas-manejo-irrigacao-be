package com.irrigation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irrigation.application.health.DatabaseHealth;
import com.irrigation.application.service.AreaService;
import com.irrigation.application.service.CropService;
import com.irrigation.application.service.RecommendationService;
import com.irrigation.application.service.SoilProfileService;
import com.irrigation.infrastructure.config.OpenApiConfig;
import com.irrigation.presentation.advice.GlobalExceptionHandler;
import com.irrigation.presentation.controller.AreaController;
import com.irrigation.presentation.controller.CropController;
import com.irrigation.presentation.controller.HealthController;
import com.irrigation.presentation.controller.RecommendationController;
import com.irrigation.presentation.controller.SoilProfileController;
import com.irrigation.presentation.mapper.AreaDtoMapperImpl;
import com.irrigation.presentation.mapper.CropDtoMapperImpl;
import com.irrigation.presentation.mapper.RecommendationDtoMapperImpl;
import com.irrigation.presentation.mapper.SoilProfileDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Verifica a especificação OpenAPI real gerada pelo Springdoc (Req 12): todos os endpoints
 * com método, caminho e resumo, respostas por código HTTP e esquemas. Sobe apenas a camada web,
 * sem banco de dados.
 */
@SpringBootTest(classes = OpenApiSmokeTest.WebOnlyApplication.class)
@AutoConfigureMockMvc
class OpenApiSmokeTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class,
            FlywayAutoConfiguration.class})
    @Import({
            AreaController.class, CropController.class, SoilProfileController.class,
            RecommendationController.class, HealthController.class,
            AreaDtoMapperImpl.class, CropDtoMapperImpl.class, SoilProfileDtoMapperImpl.class,
            RecommendationDtoMapperImpl.class, GlobalExceptionHandler.class, OpenApiConfig.class})
    static class WebOnlyApplication {
    }

    @MockBean
    AreaService areaService;
    @MockBean
    CropService cropService;
    @MockBean
    SoilProfileService soilProfileService;
    @MockBean
    RecommendationService recommendationService;
    @MockBean
    DatabaseHealth databaseHealth;

    @Autowired
    MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode apiDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void documentationEndpointReturnsAnOpenApiDocument() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        JsonNode docs = apiDocs();

        assertThat(docs.get("openapi").asText()).startsWith("3.");
        assertThat(docs.at("/info/title").asText()).isEqualTo("Irrigation Management API");
    }

    @Test
    void everyBusinessEndpointIsDocumentedWithMethodPathAndSummary() throws Exception {
        JsonNode paths = apiDocs().get("paths");
        List<String> expected = List.of(
                "POST /api/v1/areas", "GET /api/v1/areas", "GET /api/v1/areas/{id}",
                "PUT /api/v1/areas/{id}", "DELETE /api/v1/areas/{id}",
                "POST /api/v1/crops", "GET /api/v1/crops", "GET /api/v1/crops/{id}",
                "PUT /api/v1/crops/{id}", "DELETE /api/v1/crops/{id}",
                "POST /api/v1/soil-profiles", "GET /api/v1/soil-profiles", "GET /api/v1/soil-profiles/{id}",
                "PUT /api/v1/soil-profiles/{id}", "DELETE /api/v1/soil-profiles/{id}",
                "POST /api/v1/recommendations/calculate", "GET /api/v1/recommendations",
                "GET /api/v1/recommendations/{id}", "GET /api/v1/health");

        List<String> documented = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> p = paths.fields(); p.hasNext(); ) {
            Map.Entry<String, JsonNode> path = p.next();
            for (Iterator<Map.Entry<String, JsonNode>> m = path.getValue().fields(); m.hasNext(); ) {
                Map.Entry<String, JsonNode> method = m.next();
                documented.add(method.getKey().toUpperCase() + " " + path.getKey());
                assertThat(method.getValue().path("summary").asText())
                        .as("resumo de %s %s", method.getKey(), path.getKey()).isNotBlank();
            }
        }
        assertThat(documented).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(paths.fieldNames()).toIterable().allMatch(path -> path.startsWith("/api/v1"));
    }

    @Test
    void operationsDocumentSuccessAndProblemDetailsResponsesByStatusCode() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        JsonNode createArea = paths.at("/~1api~1v1~1areas/post/responses");
        assertThat(createArea.has("201")).isTrue();
        assertThat(createArea.has("400")).isTrue();
        assertThat(createArea.at("/400/content").has("application/problem+json")).isTrue();

        JsonNode getArea = paths.at("/~1api~1v1~1areas~1{id}/get/responses");
        assertThat(getArea.has("200")).isTrue();
        assertThat(getArea.has("404")).isTrue();

        JsonNode deleteArea = paths.at("/~1api~1v1~1areas~1{id}/delete/responses");
        assertThat(deleteArea.has("204")).isTrue();
        assertThat(deleteArea.has("404")).isTrue();
        assertThat(deleteArea.has("422")).isTrue();

        JsonNode calculate = paths.at("/~1api~1v1~1recommendations~1calculate/post/responses");
        assertThat(calculate.has("201")).isTrue();
        assertThat(calculate.has("400")).isTrue();
        assertThat(calculate.has("404")).isTrue();
        assertThat(calculate.has("422")).isTrue();
    }

    @Test
    void requestAndResponseSchemasAreDescribed() throws Exception {
        JsonNode docs = apiDocs();
        JsonNode schemas = docs.at("/components/schemas");

        for (String schema : List.of("AreaRequest", "AreaResponse", "CropRequest", "CropResponse",
                "SoilProfileRequest", "SoilProfileResponse", "CalculateRecommendationRequest",
                "RecommendationResponse", "HealthResponse")) {
            assertThat(schemas.has(schema)).as("esquema %s", schema).isTrue();
        }

        JsonNode createBody = docs.at("/paths/~1api~1v1~1areas/post/requestBody/content/application~1json/schema");
        assertThat(createBody.path("$ref").asText()).endsWith("/AreaRequest");

        JsonNode listParameters = docs.at("/paths/~1api~1v1~1areas/get/parameters");
        List<String> names = new ArrayList<>();
        listParameters.forEach(p -> names.add(p.get("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("page", "size", "sortBy", "direction");
    }

    @Test
    void actuatorEndpointsAreNotDocumented() throws Exception {
        assertThat(apiDocs().get("paths").fieldNames()).toIterable().noneMatch(p -> p.startsWith("/actuator"));
    }
}
