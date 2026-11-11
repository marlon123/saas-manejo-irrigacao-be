package com.irrigation.infrastructure.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carrega o appender JSON de produção ({@code logback-json.xml}) e verifica o formato real
 * das linhas de log (Req 9.4, 7.5): timestamp, severidade, mensagem e dados da exceção.
 */
class LogbackJsonConfigurationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Configura um contexto isolado com o appender JSON, executa o log e devolve cada linha como JSON. */
    private List<JsonNode> captureLogs(Consumer<Logger> logging) throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        LoggerContext context = new LoggerContext();
        // Contextos criados manualmente não recebem o MDCAdapter que o provider SLF4J injeta;
        // sem ele, o provider <mdc/> lança NPE e o evento é descartado (Logback 1.5+).
        context.setMDCAdapter(new LogbackMDCAdapter());
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback-json-test.xml"));
            logging.accept(context.getLogger("teste.json"));
            context.stop();
        } finally {
            System.setOut(originalOut);
        }
        return Arrays.stream(captured.toString(StandardCharsets.UTF_8).split("\\R"))
                .filter(line -> !line.isBlank())
                .map(line -> {
                    try {
                        return mapper.readTree(line);
                    } catch (Exception e) {
                        throw new AssertionError("Linha de log não é JSON válido: " + line, e);
                    }
                }).toList();
    }

    @Test
    void eventsWithoutExceptionAreJsonWithTimestampSeverityAndMessage() throws Exception {
        List<JsonNode> lines = captureLogs(log -> {
            log.info("evento {}", "registrado");
            log.warn("atenção");
        });

        assertThat(lines).hasSize(2);
        JsonNode info = lines.get(0);
        assertThat(info.get("severity").asText()).isEqualTo("INFO");
        assertThat(info.get("message").asText()).isEqualTo("evento registrado");
        assertThat(info.get("logger").asText()).isEqualTo("teste.json");
        assertThat(info.get("timestamp").asText()).isNotBlank();
        assertThat(info.has("exceptionType")).isFalse();
        assertThat(lines.get(1).get("severity").asText()).isEqualTo("WARN");
    }

    @Test
    void errorEventsCarryExceptionTypeMessageAndStackTrace() throws Exception {
        List<JsonNode> lines = captureLogs(log ->
                log.error("falha ao processar", new IllegalStateException("estado inválido")));

        assertThat(lines).hasSize(1);
        JsonNode error = lines.get(0);
        assertThat(error.get("severity").asText()).isEqualTo("ERROR");
        assertThat(error.get("message").asText()).isEqualTo("falha ao processar");
        assertThat(error.get("exceptionType").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(error.get("exceptionMessage").asText()).isEqualTo("estado inválido");
        assertThat(error.get("stackTrace").asText()).contains("IllegalStateException", "estado inválido");
    }
}
