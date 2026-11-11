package com.irrigation.presentation;

import com.irrigation.domain.repository.RecommendationRepository;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida completo pela API HTTP sobre o PostgreSQL real:
 * create → list → get → update → delete, integridade referencial e cálculo.
 */
class ApiLifecycleIntegrationTest extends PostgresIntegrationTest {

    private static final String PROBLEM = "application/problem+json";

    private static final String AREA = """
            {"name":"Talhão 1","areaHectares":10.5,"latitude":-23.5,"longitude":-46.6,
             "generalCharacteristics":"solo argiloso","irrigationSystemType":"DRIP",
             "systemFlowRate":120.5,"irrigationEfficiency":0.85}""";
    private static final String AREA_UPDATED = """
            {"name":"Talhão 1 ampliado","areaHectares":12.25,"irrigationSystemType":"SPRINKLER",
             "systemFlowRate":150}""";

    private static final String CROP = """
            {"name":"Milho","variety":"Híbrido","cultivationCycleDays":120,"stages":[
              {"name":"Inicial","kc":0.4,"stageOrder":1},{"name":"Pleno","kc":1.15,"stageOrder":2}]}""";
    /** Reutiliza as ordens 1 e 2 com outros estágios: depende da constraint DEFERRABLE (V2). */
    private static final String CROP_UPDATED = """
            {"name":"Milho safrinha","variety":"Híbrido","cultivationCycleDays":110,"stages":[
              {"name":"Vegetativo","kc":0.7,"stageOrder":1},{"name":"Reprodutivo","kc":1.2,"stageOrder":2},
              {"name":"Maturação","kc":0.6,"stageOrder":3}]}""";

    private static final String SOIL = """
            {"soilType":"Argiloso","fieldCapacity":0.35,"permanentWiltingPoint":0.18,"bulkDensity":1.3,
             "effectiveRootDepth":600,"availableWater":100,"soilWaterUnit":"VOLUMETRIC_WATER_CONTENT",
             "bulkDensityUnit":"GRAMS_PER_CUBIC_CENTIMETER","rootDepthUnit":"MILLIMETERS",
             "availableWaterUnit":"MILLIMETERS"}""";
    private static final String SOIL_UPDATED = """
            {"soilType":"Arenoso","fieldCapacity":0.2,"permanentWiltingPoint":0.08,"bulkDensity":1.55,
             "effectiveRootDepth":400,"availableWater":48,"soilWaterUnit":"VOLUMETRIC_WATER_CONTENT",
             "bulkDensityUnit":"GRAMS_PER_CUBIC_CENTIMETER","rootDepthUnit":"MILLIMETERS",
             "availableWaterUnit":"MILLIMETERS"}""";

    @Autowired
    MockMvc mvc;
    @Autowired
    RecommendationRepository recommendations;

    private UUID create(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(path + "/")))
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void assertDeletedAndGone(String path, UUID id) throws Exception {
        mvc.perform(delete(path + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    // =====================================================================================
    // Ciclo de vida
    // =====================================================================================

    @Test
    void areaLifecycle() throws Exception {
        UUID id = create("/api/v1/areas", AREA);

        mvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Talhão 1"))
                .andExpect(jsonPath("$.areaHectares").value(10.5))
                .andExpect(jsonPath("$.irrigationSystemType").value("DRIP"));

        mvc.perform(put("/api/v1/areas/" + id).contentType(MediaType.APPLICATION_JSON).content(AREA_UPDATED))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(jsonPath("$.name").value("Talhão 1 ampliado"))
                .andExpect(jsonPath("$.areaHectares").value(12.25))
                .andExpect(jsonPath("$.irrigationSystemType").value("SPRINKLER"));

        assertDeletedAndGone("/api/v1/areas", id);
    }

    @Test
    void areaListingIsSortedAndPaged() throws Exception {
        create("/api/v1/areas", AREA.replace("Talhão 1", "B").replace("10.5", "30"));
        create("/api/v1/areas", AREA.replace("Talhão 1", "A").replace("10.5", "20"));
        create("/api/v1/areas", AREA.replace("Talhão 1", "C").replace("10.5", "10"));

        mvc.perform(get("/api/v1/areas").param("sortBy", "areaHectares").param("direction", "desc")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("B"))
                .andExpect(jsonPath("$.content[1].name").value("A"));
    }

    @Test
    void cropLifecycleIncludingStageReplacement() throws Exception {
        UUID id = create("/api/v1/crops", CROP);

        mvc.perform(get("/api/v1/crops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id.toString())));
        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages.length()").value(2));

        mvc.perform(put("/api/v1/crops/" + id).contentType(MediaType.APPLICATION_JSON).content(CROP_UPDATED))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(jsonPath("$.name").value("Milho safrinha"))
                .andExpect(jsonPath("$.stages.length()").value(3))
                .andExpect(jsonPath("$.stages[0].name").value("Vegetativo"))
                .andExpect(jsonPath("$.stages[2].stageOrder").value(3));

        assertDeletedAndGone("/api/v1/crops", id);
        // ON DELETE CASCADE: os estágios saem junto com a cultura
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crop_stages", Integer.class)).isZero();
    }

    @Test
    void soilProfileLifecycle() throws Exception {
        UUID id = create("/api/v1/soil-profiles", SOIL);

        mvc.perform(get("/api/v1/soil-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id.toString())));
        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilType").value("Argiloso"))
                .andExpect(jsonPath("$.bulkDensityUnit").value("GRAMS_PER_CUBIC_CENTIMETER"));

        mvc.perform(put("/api/v1/soil-profiles/" + id).contentType(MediaType.APPLICATION_JSON).content(SOIL_UPDATED))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(jsonPath("$.soilType").value("Arenoso"))
                .andExpect(jsonPath("$.availableWater").value(48));

        assertDeletedAndGone("/api/v1/soil-profiles", id);
    }

    // =====================================================================================
    // Integridade referencial (Req 1.19)
    // =====================================================================================

    @Test
    void deletingAnAreaReferencedByARecommendationReturns422AndKeepsTheArea() throws Exception {
        UUID areaId = create("/api/v1/areas", AREA);
        recommendations.save(TestData.recommendation(areaId, LocalDateTime.of(2025, 1, 15, 6, 0)));

        mvc.perform(delete("/api/v1/areas/" + areaId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail", containsString(areaId.toString())));

        mvc.perform(get("/api/v1/areas/" + areaId)).andExpect(status().isOk());
    }

    /** Rede de segurança: mesmo sem a checagem do serviço, a FK impede a exclusão. */
    @Test
    void theForeignKeyRejectsDeletingAReferencedAreaDirectly() throws Exception {
        UUID areaId = create("/api/v1/areas", AREA);
        recommendations.save(TestData.recommendation(areaId, LocalDateTime.of(2025, 1, 15, 6, 0)));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM areas WHERE id = ?", areaId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // =====================================================================================
    // Cálculo de recomendação
    // =====================================================================================

    private static String calculateBody(Object areaId, String methodology) {
        return """
                {"areaId":"%s","methodologyId":"%s","inputs":{
                  "eto":5,"kc":1.0,"etc":5,"effectivePrecipitation":0,"currentSoilMoisture":50,
                  "waterStorageCapacity":100,"irrigationEfficiency":0.8,"netIrrigationDepth":20,
                  "grossIrrigationDepth":25,"irrigatedArea":10,"systemFlowRate":100,"irrigationTime":2}}"""
                .formatted(areaId, methodology);
    }

    @Test
    void calculatingStoresTheRecommendationWithItsSnapshot() throws Exception {
        UUID areaId = create("/api/v1/areas", AREA);

        MvcResult result = mvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON).content(calculateBody(areaId, FAKE_METHODOLOGY)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/recommendations/")))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/recommendations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areaId").value(areaId.toString()))
                .andExpect(jsonPath("$.methodologyId").value(FAKE_METHODOLOGY))
                .andExpect(jsonPath("$.methodologyVersion").value("1.0"))
                .andExpect(jsonPath("$.inputs.eto").value(5))
                .andExpect(jsonPath("$.inputs.irrigatedArea").value(10))
                .andExpect(jsonPath("$.results.waterVolume").value(2000));
    }

    @Test
    void calculatingForAMissingAreaReturns404() throws Exception {
        mvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON).content(calculateBody(UUID.randomUUID(), FAKE_METHODOLOGY)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
        assertThat(recommendations.count()).isZero();
    }

    @Test
    void calculatingWithAnUnregisteredMethodologyReturns422() throws Exception {
        UUID areaId = create("/api/v1/areas", AREA);

        mvc.perform(post("/api/v1/recommendations/calculate")
                        .contentType(MediaType.APPLICATION_JSON).content(calculateBody(areaId, "inexistente")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Metodologia de cálculo não disponível: inexistente"));
        assertThat(recommendations.count()).isZero();
    }
}
