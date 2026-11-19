package com.irrigation.presentation;

import com.irrigation.support.PostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastros só com os campos obrigatórios, pela API HTTP sobre o PostgreSQL real, e leitura de
 * registros com colunas nulas (Req 1.6, 1.11, 2.5, 2.7, 3.1, 3.4, 16.1 do módulo de balanço hídrico).
 */
class OptionalFieldsApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    private UUID create(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    // ---------------------------------------------------------------- Área

    @Test
    void areaWithOnlyTheNameIsCreatedReadAndUpdated() throws Exception {
        UUID id = create("/api/v1/areas", "{\"name\":\"Só o nome\"}");

        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Só o nome"))
                .andExpect(jsonPath("$.areaHectares").value(nullValue()))
                .andExpect(jsonPath("$.irrigationSystemType").value(nullValue()))
                .andExpect(jsonPath("$.systemFlowRate").value(nullValue()));

        mvc.perform(put("/api/v1/areas/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renomeada\",\"areaHectares\":12.5}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/areas/" + id))
                .andExpect(jsonPath("$.name").value("Renomeada"))
                .andExpect(jsonPath("$.areaHectares").value(12.5))
                .andExpect(jsonPath("$.irrigationSystemType").value(nullValue()));
    }

    @Test
    void areaListingWithNullHectaresPutsThemLastInBothDirections() throws Exception {
        create("/api/v1/areas", "{\"name\":\"Sem área\"}");
        create("/api/v1/areas", "{\"name\":\"Grande\",\"areaHectares\":50}");
        create("/api/v1/areas", "{\"name\":\"Pequena\",\"areaHectares\":5}");

        mvc.perform(get("/api/v1/areas").param("sortBy", "areaHectares").param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Pequena"))
                .andExpect(jsonPath("$.content[1].name").value("Grande"))
                .andExpect(jsonPath("$.content[2].name").value("Sem área"))
                .andExpect(jsonPath("$.content[2].areaHectares").value(nullValue()));
        mvc.perform(get("/api/v1/areas").param("sortBy", "areaHectares").param("direction", "desc"))
                .andExpect(jsonPath("$.content[0].name").value("Grande"))
                .andExpect(jsonPath("$.content[1].name").value("Pequena"))
                .andExpect(jsonPath("$.content[2].name").value("Sem área"));
    }

    // ---------------------------------------------------------------- Cultura

    @Test
    void cropWithOnlyNameKcAndRootDepthHasZeroStagesAndIsReadAndUpdated() throws Exception {
        UUID id = create("/api/v1/crops", "{\"name\":\"Milho\",\"kc\":1.15,\"rootDepthMm\":450.50}");

        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kc").value(1.15))
                .andExpect(jsonPath("$.rootDepthMm").value(450.5))
                .andExpect(jsonPath("$.variety").value(nullValue()))
                .andExpect(jsonPath("$.cultivationCycleDays").value(nullValue()))
                .andExpect(jsonPath("$.stages.length()").value(0));

        mvc.perform(put("/api/v1/crops/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Milho\",\"kc\":0.9,\"rootDepthMm\":300,\"stages\":["
                                + "{\"name\":\"Pleno\",\"kc\":1.1,\"stageOrder\":1}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(jsonPath("$.kc").value(0.9))
                .andExpect(jsonPath("$.stages.length()").value(1));
    }

    /** Cultura gravada antes do módulo (sem kc e sem root_depth_mm) continua legível. */
    @Test
    void legacyCropRowWithNullKcAndRootDepthIsReadWithNulls() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO crops (id, name) VALUES (?, 'Antiga')", id);

        mvc.perform(get("/api/v1/crops/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Antiga"))
                .andExpect(jsonPath("$.kc").value(nullValue()))
                .andExpect(jsonPath("$.rootDepthMm").value(nullValue()))
                .andExpect(jsonPath("$.stages.length()").value(0));
    }

    // ---------------------------------------------------------------- Solo

    @Test
    void soilWithOnlyTypeGravimetricPairAndDensityIsCreatedReadAndUpdated() throws Exception {
        UUID id = create("/api/v1/soil-profiles", "{\"soilType\":\"Argiloso\","
                + "\"fieldCapacityGravimetricPct\":28.50,\"permanentWiltingPointGravimetricPct\":14.20,"
                + "\"bulkDensity\":1.30}");

        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(28.5))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(14.2))
                .andExpect(jsonPath("$.bulkDensity").value(1.3))
                .andExpect(jsonPath("$.fieldCapacity").value(nullValue()))
                .andExpect(jsonPath("$.permanentWiltingPoint").value(nullValue()))
                .andExpect(jsonPath("$.effectiveRootDepth").value(nullValue()))
                .andExpect(jsonPath("$.availableWater").value(nullValue()))
                .andExpect(jsonPath("$.soilWaterUnit").value(nullValue()))
                .andExpect(jsonPath("$.bulkDensityUnit").value(nullValue()));

        mvc.perform(put("/api/v1/soil-profiles/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soilType\":\"Arenoso\",\"bulkDensity\":1.55}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(jsonPath("$.soilType").value("Arenoso"))
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(nullValue()))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(nullValue()));
    }

    /** Par gravimétrico incompleto: 400 e nada gravado (A-07). */
    @Test
    void soilWithIncompleteGravimetricPairIsRejectedAndNotStored() throws Exception {
        mvc.perform(post("/api/v1/soil-profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soilType\":\"Argiloso\",\"fieldCapacityGravimetricPct\":28.5,\"bulkDensity\":1.3}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/soil-profiles"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** Solo gravado antes do módulo, com as colunas gravimétricas nulas, continua legível. */
    @Test
    void legacySoilRowWithNullGravimetricColumnsIsReadWithNulls() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO soil_profiles (id, soil_type, field_capacity, permanent_wilting_point, "
                + "bulk_density, effective_root_depth, available_water, soil_water_unit, bulk_density_unit, "
                + "root_depth_unit, available_water_unit) VALUES (?, 'Antigo', 0.35, 0.18, 1.30, 600, 100, "
                + "'VOLUMETRIC_WATER_CONTENT', 'GRAMS_PER_CUBIC_CENTIMETER', 'MILLIMETERS', 'MILLIMETERS')", id);

        mvc.perform(get("/api/v1/soil-profiles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldCapacity").value(0.35))
                .andExpect(jsonPath("$.fieldCapacityGravimetricPct").value(nullValue()))
                .andExpect(jsonPath("$.permanentWiltingPointGravimetricPct").value(nullValue()));
    }
}
