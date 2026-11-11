package com.irrigation.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irrigation.domain.repository.AreaRepository;
import com.irrigation.domain.repository.RecommendationRepository;
import com.irrigation.support.PostgresIntegrationTest;
import com.irrigation.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logs estruturados da aplicação completa (Req 7.5, 9.4): fora do perfil {@code dev}, cada
 * linha de log é um objeto JSON; erros trazem tipo, mensagem e stack trace da exceção.
 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingIntegrationTest extends PostgresIntegrationTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    MockMvc mvc;
    @Autowired
    AreaRepository areas;
    @Autowired
    RecommendationRepository recommendations;

    /** Linhas JSON emitidas pelo logger informado. */
    private List<JsonNode> jsonLines(CapturedOutput output, String loggerPrefix) {
        return output.getOut().lines()
                .filter(line -> line.startsWith("{"))
                .map(line -> {
                    try {
                        return json.readTree(line);
                    } catch (Exception e) {
                        throw new AssertionError("Linha de log não é JSON válido: " + line, e);
                    }
                })
                .filter(node -> node.path("logger").asText().startsWith(loggerPrefix))
                .toList();
    }

    @Test
    void creatingAnAreaLogsAnInfoJsonEvent(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/v1/areas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Talhão log","areaHectares":5,"irrigationSystemType":"DRIP","systemFlowRate":50}"""))
                .andExpect(status().isCreated());

        assertThat(jsonLines(output, "com.irrigation.application.service.AreaService"))
                .anySatisfy(line -> {
                    assertThat(line.path("severity").asText()).isEqualTo("INFO");
                    assertThat(line.path("message").asText()).startsWith("Área criada: id=");
                    assertThat(line.path("timestamp").asText()).isNotBlank();
                });
    }

    @Test
    void rejectedRequestsLogAWarnWithTheExceptionDetails(CapturedOutput output) throws Exception {
        UUID areaId = areas.save(TestData.area()).getId();
        recommendations.save(TestData.recommendation(areaId, LocalDateTime.of(2025, 1, 1, 0, 0)));

        mvc.perform(delete("/api/v1/areas/" + areaId)).andExpect(status().isUnprocessableEntity());

        assertThat(jsonLines(output, "com.irrigation.presentation.advice"))
                .anySatisfy(line -> {
                    assertThat(line.path("severity").asText()).isEqualTo("WARN");
                    assertThat(line.path("exceptionType").asText())
                            .isEqualTo("com.irrigation.domain.exception.BusinessRuleException");
                    assertThat(line.path("exceptionMessage").asText()).contains(areaId.toString());
                    assertThat(line.path("stackTrace").asText()).contains("BusinessRuleException");
                });
    }
}
