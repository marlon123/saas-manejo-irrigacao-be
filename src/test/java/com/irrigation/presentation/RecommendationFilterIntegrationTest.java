package com.irrigation.presentation;

import com.irrigation.domain.model.Recommendation;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.RecommendationRepository;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consultas filtradas de recomendações no PostgreSQL real (Req 5.2–5.4, 5.8).
 *
 * <p>Cenário: duas áreas, com recomendações em janeiro, fevereiro e março de 2025.
 */
class RecommendationFilterIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    AreaRepository areas;
    @Autowired
    RecommendationRepository recommendations;

    private UUID areaA;
    private UUID areaB;
    private String aJan;
    private String aFeb;
    private String aMar;
    private String bFeb;

    private String save(UUID areaId, LocalDateTime dateTime) {
        Recommendation r = recommendations.save(TestData.recommendation(areaId, dateTime));
        return r.getId().toString();
    }

    @BeforeEach
    void scenario() {
        areaA = areas.save(TestData.area(UUID.randomUUID(), "Área A", "10")).getId();
        areaB = areas.save(TestData.area(UUID.randomUUID(), "Área B", "20")).getId();
        aJan = save(areaA, LocalDateTime.of(2025, 1, 10, 6, 0));
        aFeb = save(areaA, LocalDateTime.of(2025, 2, 10, 6, 0));
        aMar = save(areaA, LocalDateTime.of(2025, 3, 10, 6, 0));
        bFeb = save(areaB, LocalDateTime.of(2025, 2, 15, 6, 0));
    }

    @Test
    void withoutFiltersReturnsEverythingNewestFirst() throws Exception {
        mvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].id").value(aMar))
                .andExpect(jsonPath("$.content[3].id").value(aJan));
    }

    @Test
    void filtersByArea() throws Exception {
        mvc.perform(get("/api/v1/recommendations").param("areaId", areaA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(aJan, aFeb, aMar)));
    }

    @Test
    void filtersByDateRangeInclusiveOnBothEnds() throws Exception {
        mvc.perform(get("/api/v1/recommendations")
                        .param("dateFrom", "2025-02-10T06:00:00")
                        .param("dateTo", "2025-02-15T06:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(aFeb, bFeb)));
    }

    @Test
    void filtersByOpenEndedDateRange() throws Exception {
        mvc.perform(get("/api/v1/recommendations").param("dateFrom", "2025-02-12T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(bFeb, aMar)));
        mvc.perform(get("/api/v1/recommendations").param("dateTo", "2025-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(aJan)));
    }

    @Test
    void filtersByAreaAndDateRange() throws Exception {
        mvc.perform(get("/api/v1/recommendations")
                        .param("areaId", areaA.toString())
                        .param("dateFrom", "2025-02-01T00:00:00")
                        .param("dateTo", "2025-03-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(aFeb, aMar)));
    }

    @Test
    void returnsAnEmptyPageWhenNothingMatches() throws Exception {
        mvc.perform(get("/api/v1/recommendations")
                        .param("areaId", areaB.toString())
                        .param("dateFrom", "2025-03-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void paginatesFilteredResults() throws Exception {
        mvc.perform(get("/api/v1/recommendations").param("areaId", areaA.toString())
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(aJan));
    }
}
