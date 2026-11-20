package com.irrigation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irrigation.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compatibilidade retroativa da API (Req 1.10, 1.11, 2.6, 2.7, 3.4, 16.1, 16.2 do módulo de balanço
 * hídrico), via HTTP sobre o PostgreSQL real: requisições no formato antigo de clientes existentes,
 * registros anteriores à V4 e contrato de recomendação, saúde e OpenAPI.
 */
class BackwardCompatibilityApiIntegrationTest extends PostgresIntegrationTest {

    private static final String PROBLEM = "application/problem+json";

    @Autowired
    MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UUID create(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    // ---------------------------------------------------------------- Formato antigo

    @Test
    void oldFormatAreaWithAllFieldsKeepsTheSameSuccessResponses() throws Exception {
        String body = """
                {"name":"Talhão antigo","areaHectares":10.5,"latitude":-23.5,"longitude":-46.6,
                 "generalCharacteristics":"solo argiloso","irrigationSystemType":"DRIP",
                 "systemFlowRate":120.5,"irrigationEfficiency":0.85}""";
        UUID id = create("/api/v1/areas", body);

        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Talhão antigo"))
                .andExpect(jsonPath("$.areaHectares").value(10.5))
                .andExpect(jsonPath("$.latitude").value(-23.5))
                .andExpect(jsonPath("$.longitude").value(-46.6))
                .andExpect(jsonPath("$.generalCharacteristics").value("solo argiloso"))
                .andExpect(jsonPath("$.irrigationSystemType").value("DRIP"))
                .andExpect(jsonPath("$.systemFlowRate").value(120.5))
                .andExpect(jsonPath("$.irrigationEfficiency").value(0.85));
        mvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/v1/areas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Talhão antigo", "Talhão editado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Talhão editado"));
    }

    @Test
    void oldFormatSoilWithVolumetricFieldsAndUnitsAndNoGravimetricFieldsKeepsTheSameResponses() throws Exception {
        String body = """
                {"soilType":"Argiloso","fieldCapacity":0.35,"permanentWiltingPoint":0.18,"bulkDensity":1.3,
                 "effectiveRootDepth":600,"availableWater":100,"soilWaterUnit":"VOLUMETRIC_WATER_CONTENT",
                 "bulkDensityUnit":"GRAMS_PER_CUBIC_CENTIMETER","rootDepthUnit":"MILLIMETERS",
                 "availableWaterUnit":"MILLIMETERS"}""";
        UUID id = create("/api/v1/soil-profiles", body);

        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilType").value("Argiloso"))
                .andExpect(jsonPath("$.fieldCapacity").value(0.35))
                .andExpect(jsonPath("$.permanentWiltingPoint").value(0.18))
                .andExpect(jsonPath("$.bulkDensity").value(1.3))
                .andExpect(jsonPath("$.effectiveRootDepth").value(600))
                .andExpect(jsonPath("$.availableWater").value(100))
                .andExpect(jsonPath("$.soilWaterUnit").value("VOLUMETRIC_WATER_CONTENT"))
                .andExpect(jsonPath("$.bulkDensityUnit").value("GRAMS_PER_CUBIC_CENTIMETER"))
                .andExpect(jsonPath("$.rootDepthUnit").value("MILLIMETERS"))
                .andExpect(jsonPath("$.availableWaterUnit").value("MILLIMETERS"))
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(nullValue()))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(nullValue()));
        mvc.perform(put("/api/v1/soil-profiles/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Argiloso", "Argiloso editado")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(jsonPath("$.soilType").value("Argiloso editado"));
    }

    @Test
    void oldFormatCropWithStagesAndWithoutKcAndRootDepthIsNowRejectedWith400() throws Exception {
        // MUDANÇA INTENCIONAL (Req 2.10): kc e rootDepthMm passaram a ser obrigatórios na API.
        // O formato antigo (só nome, variedade, ciclo e estágios) agora retorna 400, e nada é gravado.
        String oldFormat = """
                {"name":"Milho","variety":"Híbrido","cultivationCycleDays":120,"stages":[
                  {"name":"Inicial","kc":0.4,"stageOrder":1},{"name":"Pleno","kc":1.15,"stageOrder":2}]}""";

        mvc.perform(post("/api/v1/crops").contentType(MediaType.APPLICATION_JSON).content(oldFormat))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.violations[*].field").value(org.hamcrest.Matchers.hasItems("kc", "rootDepthMm")));
        mvc.perform(get("/api/v1/crops")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void oldFormatCropPlusKcAndRootDepthKeepsTheExistingFieldsAndStages() throws Exception {
        UUID id = create("/api/v1/crops", """
                {"name":"Milho","variety":"Híbrido","cultivationCycleDays":120,"stages":[
                  {"name":"Inicial","kc":0.4,"stageOrder":1},{"name":"Pleno","kc":1.15,"stageOrder":2}],
                 "kc":1.0,"rootDepthMm":400}""");

        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Milho"))
                .andExpect(jsonPath("$.variety").value("Híbrido"))
                .andExpect(jsonPath("$.cultivationCycleDays").value(120))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[0].name").value("Inicial"))
                .andExpect(jsonPath("$.stages[1].kc").value(1.15))
                .andExpect(jsonPath("$.stages[1].stageOrder").value(2));
    }

    // ---------------------------------------------------------------- Recomendação

    @Test
    void calculateKeepsItsContractWhenNoProductionMethodologyIsRegistered() throws Exception {
        UUID areaId = create("/api/v1/areas", """
                {"name":"A","areaHectares":10,"irrigationSystemType":"DRIP","systemFlowRate":100}""");
        String body = """
                {"areaId":"%s","methodologyId":"%s","inputs":{
                  "eto":5,"kc":1.0,"etc":5,"effectivePrecipitation":0,"currentSoilMoisture":50,
                  "waterStorageCapacity":100,"irrigationEfficiency":0.8,"netIrrigationDepth":20,
                  "grossIrrigationDepth":25,"irrigatedArea":10,"systemFlowRate":100,"irrigationTime":2}}""";

        // Só a metodologia falsa dos testes existe: qualquer outra (inclusive nenhuma de produção) dá 422.
        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(areaId, "penman-monteith")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value("Metodologia de cálculo não disponível: penman-monteith"));
        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(UUID.randomUUID(), FAKE_METHODOLOGY)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/recommendations/calculate").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(areaId, FAKE_METHODOLOGY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areaId").value(areaId.toString()))
                .andExpect(jsonPath("$.methodologyId").value(FAKE_METHODOLOGY))
                .andExpect(jsonPath("$.inputs.eto").value(5))
                .andExpect(jsonPath("$.results.waterVolume").value(2000));
    }

    // ---------------------------------------------------------------- Registros anteriores à V4

    @Test
    void preV4AreaRowIsReadAndEditable() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO areas (id, name, area_ha, latitude, longitude, general_characteristics, "
                + "irrigation_system_type, system_flow_rate, irrigation_efficiency) "
                + "VALUES (?, 'Antiga', 10.50, -23.5, -46.6, 'argiloso', 'DRIP', 120.50, 0.8500)", id);

        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Antiga"))
                .andExpect(jsonPath("$.areaHectares").value(10.5))
                .andExpect(jsonPath("$.irrigationSystemType").value("DRIP"))
                .andExpect(jsonPath("$.systemFlowRate").value(120.5))
                .andExpect(jsonPath("$.irrigationEfficiency").value(0.85));
        mvc.perform(put("/api/v1/areas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Antiga editada\",\"areaHectares\":10.5,\"latitude\":-23.5,"
                                + "\"longitude\":-46.6,\"generalCharacteristics\":\"argiloso\","
                                + "\"irrigationSystemType\":\"DRIP\",\"systemFlowRate\":120.5,"
                                + "\"irrigationEfficiency\":0.85}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(jsonPath("$.name").value("Antiga editada"))
                .andExpect(jsonPath("$.areaHectares").value(10.5))
                .andExpect(jsonPath("$.systemFlowRate").value(120.5));
    }

    @Test
    void preV4CropRowWithStagesAndNullKcAndRootDepthIsReadAndEditableKeepingValues() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO crops (id, name, variety, cultivation_cycle_days, kc, root_depth_mm) "
                + "VALUES (?, 'Antiga', 'Híbrido', 120, NULL, NULL)", id);
        jdbc.update("INSERT INTO crop_stages (crop_id, name, kc, stage_order) VALUES (?, 'Inicial', 0.40, 1)", id);
        jdbc.update("INSERT INTO crop_stages (crop_id, name, kc, stage_order) VALUES (?, 'Pleno', 1.15, 2)", id);

        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Antiga"))
                .andExpect(jsonPath("$.variety").value("Híbrido"))
                .andExpect(jsonPath("$.cultivationCycleDays").value(120))
                .andExpect(jsonPath("$.kc").value(nullValue()))
                .andExpect(jsonPath("$.rootDepthMm").value(nullValue()))
                .andExpect(jsonPath("$.stages.length()").value(2))
                .andExpect(jsonPath("$.stages[1].kc").value(1.15));

        // Edição (PUT exige kc e rootDepthMm, Req 2.10); os demais valores permanecem.
        mvc.perform(put("/api/v1/crops/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Antiga\",\"variety\":\"Híbrido\",\"cultivationCycleDays\":120,"
                                + "\"stages\":[{\"name\":\"Inicial\",\"kc\":0.4,\"stageOrder\":1},"
                                + "{\"name\":\"Pleno\",\"kc\":1.15,\"stageOrder\":2}],\"kc\":1.0,\"rootDepthMm\":400}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(jsonPath("$.variety").value("Híbrido"))
                .andExpect(jsonPath("$.cultivationCycleDays").value(120))
                .andExpect(jsonPath("$.kc").value(1.0))
                .andExpect(jsonPath("$.rootDepthMm").value(400))
                .andExpect(jsonPath("$.stages.length()").value(2));
    }

    @Test
    void preV4SoilRowWithNullGravimetricColumnsIsReadAndEditableKeepingValues() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO soil_profiles (id, soil_type, field_capacity, permanent_wilting_point, "
                + "bulk_density, effective_root_depth, available_water, soil_water_unit, bulk_density_unit, "
                + "root_depth_unit, available_water_unit, field_capacity_gravimetric_pct, "
                + "permanent_wilting_point_gravimetric_pct) VALUES (?, 'Antigo', 0.35, 0.18, 1.30, 600, 100, "
                + "'VOLUMETRIC_WATER_CONTENT', 'GRAMS_PER_CUBIC_CENTIMETER', 'MILLIMETERS', 'MILLIMETERS', "
                + "NULL, NULL)", id);

        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldCapacity").value(0.35))
                .andExpect(jsonPath("$.permanentWiltingPoint").value(0.18))
                .andExpect(jsonPath("$.bulkDensity").value(1.3))
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(nullValue()));

        mvc.perform(put("/api/v1/soil-profiles/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soilType\":\"Antigo editado\",\"fieldCapacity\":0.35,"
                                + "\"permanentWiltingPoint\":0.18,\"bulkDensity\":1.3,\"effectiveRootDepth\":600,"
                                + "\"availableWater\":100,\"soilWaterUnit\":\"VOLUMETRIC_WATER_CONTENT\","
                                + "\"bulkDensityUnit\":\"GRAMS_PER_CUBIC_CENTIMETER\","
                                + "\"rootDepthUnit\":\"MILLIMETERS\",\"availableWaterUnit\":\"MILLIMETERS\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(jsonPath("$.soilType").value("Antigo editado"))
                .andExpect(jsonPath("$.fieldCapacity").value(0.35))
                .andExpect(jsonPath("$.permanentWiltingPoint").value(0.18))
                .andExpect(jsonPath("$.availableWater").value(100))
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(nullValue()));
    }

    // ---------------------------------------------------------------- Saúde

    @Test
    void healthEndpointsRespondAsBefore() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.application").value("healthy"))
                .andExpect(jsonPath("$.database").value("connected"));
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    // ---------------------------------------------------------------- OpenAPI com o contexto completo

    @Test
    void openApiListsTheWaterBalancePathsAndTheConflictAndUnprocessableResponses() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = mapper.readTree(body).get("paths");

        String areaBase = "/api/v1/areas/{areaId}";
        for (String path : new String[]{
                areaBase + "/management-plans", areaBase + "/daily-weather", areaBase + "/applied-irrigation",
                areaBase + "/water-balance/calculate", areaBase + "/water-balance/reprocess",
                areaBase + "/water-balance/{date}", areaBase + "/water-balance/{date}/revisions"}) {
            assertThat(paths.has(path)).as("caminho %s", path).isTrue();
        }
        // Caminhos antigos continuam documentados.
        assertThat(paths.has("/api/v1/recommendations/calculate")).isTrue();
        assertThat(paths.has("/api/v1/areas/{id}")).isTrue();

        for (String op : new String[]{"/water-balance/calculate", "/water-balance/reprocess"}) {
            JsonNode responses = paths.get(areaBase + op).get("post").get("responses");
            assertThat(responses.has("409")).as("409 em %s", op).isTrue();
            assertThat(responses.has("422")).as("422 em %s", op).isTrue();
            assertThat(responses.at("/409/content").has("application/problem+json")).isTrue();
        }
        JsonNode createPlan = paths.get(areaBase + "/management-plans").get("post").get("responses");
        assertThat(createPlan.has("422")).isTrue();
    }
}
