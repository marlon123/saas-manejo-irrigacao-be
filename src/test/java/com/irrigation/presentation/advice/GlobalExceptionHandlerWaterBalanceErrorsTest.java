package com.irrigation.presentation.advice;

import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.ConflictException;
import com.irrigation.domain.exception.EntityNotFoundException;
import com.irrigation.domain.exception.ImmutableRecordException;
import com.irrigation.domain.exception.PreconditionsNotMetException;
import com.irrigation.domain.exception.PreconditionsNotMetException.Code;
import com.irrigation.domain.exception.PreconditionsNotMetException.Precondition;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do {@link GlobalExceptionHandler} para os erros do módulo de balanço hídrico
 * (Req 11.5, 12.8, 12.10, 16.1): prevalência do handler específico, formato Problem_Details
 * e preservação dos handlers anteriores. Usa um controlador de teste que lança cada exceção.
 */
class GlobalExceptionHandlerWaterBalanceErrorsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 15);

    @RestController
    static class ThrowingController {

        @GetMapping("/test/preconditions")
        void preconditions() {
            throw new PreconditionsNotMetException(DATE, List.of(
                    new Precondition(Code.WEATHER_MISSING, "Dados climáticos ausentes", List.of("eto", "rainfall")),
                    new Precondition(Code.APPLIED_IRRIGATION_MISSING, "Irrigação aplicada ausente")));
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("Já existe um processamento em andamento para a área");
        }

        @GetMapping("/test/immutable")
        void immutable() {
            throw new ImmutableRecordException();
        }

        @GetMapping("/test/business-rule")
        void businessRule() {
            throw new BusinessRuleException("regra genérica violada");
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new EntityNotFoundException("Área", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        }

        @GetMapping("/test/validation")
        void validation() {
            throw new ValidationFailedException(List.of(new FieldViolation("name", "obrigatório")));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void preconditionsNotMetPrevailsOverBusinessRuleAndExposesDateAndPreconditions() throws Exception {
        mvc.perform(get("/test/preconditions"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Violação de regra de negócio"))
                .andExpect(jsonPath("$.detail").value("Pré-condições não atendidas para a data 2026-03-15"))
                .andExpect(jsonPath("$.date").value("2026-03-15"))
                .andExpect(jsonPath("$.preconditions", hasSize(2)))
                .andExpect(jsonPath("$.preconditions[0].code").value("WEATHER_MISSING"))
                .andExpect(jsonPath("$.preconditions[0].message").value("Dados climáticos ausentes"))
                .andExpect(jsonPath("$.preconditions[0].fields[0]").value("eto"))
                .andExpect(jsonPath("$.preconditions[0].fields[1]").value("rainfall"))
                .andExpect(jsonPath("$.preconditions[1].code").value("APPLIED_IRRIGATION_MISSING"))
                .andExpect(jsonPath("$.preconditions[1].fields", hasSize(0)));
    }

    @Test
    void conflictReturns409WithConflitoTitle() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflito"))
                .andExpect(jsonPath("$.detail").value("Já existe um processamento em andamento para a área"));
    }

    @Test
    void immutableRecordPrevailsOverBusinessRuleAndReturns422WithoutExtensions() throws Exception {
        mvc.perform(get("/test/immutable"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Violação de regra de negócio"))
                .andExpect(jsonPath("$.detail").value(ImmutableRecordException.DEFAULT_MESSAGE))
                .andExpect(jsonPath("$.date").doesNotExist())
                .andExpect(jsonPath("$.preconditions").doesNotExist());
    }

    @Test
    void genericBusinessRuleStillReturns422WithoutExtensions() throws Exception {
        mvc.perform(get("/test/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Violação de regra de negócio"))
                .andExpect(jsonPath("$.detail").value("regra genérica violada"))
                .andExpect(jsonPath("$.date").doesNotExist())
                .andExpect(jsonPath("$.preconditions").doesNotExist());
    }

    @Test
    void notFoundStillReturns404() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"));
    }

    @Test
    void validationFailureStillReturns400WithViolations() throws Exception {
        mvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message").value("obrigatório"));
    }
}
