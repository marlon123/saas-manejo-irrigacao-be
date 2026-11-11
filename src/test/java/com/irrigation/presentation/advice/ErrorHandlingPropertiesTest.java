package com.irrigation.presentation.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.calculation.MethodologyIdentifier;
import com.irrigation.domain.calculation.exception.DuplicateMethodologyException;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades de correção 14 a 16 do design: toda falha vira RFC 7807 com o status correto
 * e, no caso de erros inesperados, sem vazar detalhes internos.
 */
@PropertyDefaults(tries = 200)
class ErrorHandlingPropertiesTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

    private static ProblemDetail problemOf(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        return (ProblemDetail) response.getBody();
    }

    private static void assertStandardMembers(ProblemDetail problem, HttpStatus status) {
        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
    }

    @Provide
    Arbitrary<String> words() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    /** O jqwik não possui arbitrário padrão para UUID. */
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> new UUID(random.nextLong(), random.nextLong()));
    }

    @Provide
    Arbitrary<List<FieldViolation>> violations() {
        Arbitrary<FieldViolation> violation = Combinators
                .combine(words(), words()).as(FieldViolation::new);
        return violation.list().ofMinSize(1).ofMaxSize(5);
    }

    // =====================================================================================
    // Propriedade 14: erros de validação → 400 com violações de campo
    // =====================================================================================

    // Feature: irrigation-management-backend, Property 14: validação retorna 400 com violations
    @Property
    void validationFailuresReturnProblemDetailsWithEveryFieldViolation(@ForAll("violations") List<FieldViolation> list) {
        ProblemDetail problem = problemOf(handler.handleValidationFailed(new ValidationFailedException(list)));

        assertStandardMembers(problem, HttpStatus.BAD_REQUEST);
        assertThat(problem.getProperties()).containsKey("violations");
        assertThat(problem.getProperties().get("violations")).isEqualTo(list);
    }

    // Feature: irrigation-management-backend, Property 14: parâmetros de cálculo inválidos
    @Property
    void invalidCalculationInputReturns400IdentifyingEachParameter(@ForAll("parameterNames") List<String> names) {
        ProblemDetail problem = problemOf(handler.handleInvalidCalculationInput(new InvalidCalculationInputException(names)));

        assertStandardMembers(problem, HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        List<FieldViolation> violations = (List<FieldViolation>) problem.getProperties().get("violations");
        assertThat(violations).extracting(FieldViolation::field).containsExactlyElementsOf(names);
        assertThat(violations).allSatisfy(v -> assertThat(v.message()).isNotBlank());
    }

    @Provide
    Arbitrary<List<String>> parameterNames() {
        return words().list().ofMinSize(1).ofMaxSize(12);
    }

    // =====================================================================================
    // Propriedade 15: 404 e 422 com Problem Details
    // =====================================================================================

    // Feature: irrigation-management-backend, Property 15: recurso inexistente → 404 identificando o recurso
    @Property
    void missingResourcesReturn404IdentifyingTheResource(@ForAll("uuids") UUID id, @ForAll("words") String type) {
        ProblemDetail problem = problemOf(handler.handleNotFound(new EntityNotFoundException(type, id)));

        assertStandardMembers(problem, HttpStatus.NOT_FOUND);
        assertThat(problem.getDetail()).contains(id.toString()).contains(type);
    }

    // Feature: irrigation-management-backend, Property 15: regra de negócio → 422 descrevendo a regra
    @Property
    void businessRuleViolationsReturn422DescribingTheRule(@ForAll("words") String message) {
        ProblemDetail problem = problemOf(handler.handleBusinessRule(new BusinessRuleException(message)));

        assertStandardMembers(problem, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(problem.getDetail()).isEqualTo(message);
    }

    // Feature: irrigation-management-backend, Property 15: metodologia indisponível ou duplicada → 422
    @Property
    void methodologyErrorsReturn422(@ForAll("words") String methodology) {
        MethodologyIdentifier id = new MethodologyIdentifier(methodology);

        ProblemDetail unavailable = problemOf(handler.handleBusinessRule(new MethodologyNotAvailableException(id)));
        ProblemDetail duplicate = problemOf(handler.handleBusinessRule(new DuplicateMethodologyException(id)));

        assertStandardMembers(unavailable, HttpStatus.UNPROCESSABLE_ENTITY);
        assertStandardMembers(duplicate, HttpStatus.UNPROCESSABLE_ENTITY);
        // Mensagem exata: o identificador aparece como texto, sem o toString() do record
        assertThat(unavailable.getDetail()).isEqualTo("Metodologia de cálculo não disponível: " + methodology);
        assertThat(duplicate.getDetail()).isEqualTo("Metodologia já registrada com identificador: " + methodology);
    }

    // =====================================================================================
    // Propriedade 16: erros inesperados nunca vazam detalhes internos
    // =====================================================================================

    @Provide
    Arbitrary<Exception> unexpectedExceptions() {
        Arbitrary<String> secrets = Arbitraries.strings().alpha().ofMinLength(8).ofMaxLength(30)
                .map(s -> "segredo" + s);
        return secrets.flatMap(secret -> Arbitraries.of(
                new RuntimeException(secret),
                new IllegalStateException(secret, new NullPointerException(secret)),
                new UnsupportedOperationException(secret),
                new ArrayIndexOutOfBoundsException(secret)));
    }

    // Feature: irrigation-management-backend, Property 16: 500 genérico sem vazamento
    @Property
    void unexpectedErrorsReturnAGenericProblemWithoutInternalDetails(@ForAll("unexpectedExceptions") Exception ex)
            throws Exception {
        ProblemDetail problem = problemOf(handler.handleUnexpected(ex));

        assertStandardMembers(problem, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.DETAIL_UNEXPECTED);

        String body = json.writeValueAsString(problem);
        assertThat(body)
                .doesNotContain(ex.getMessage())
                .doesNotContain(ex.getClass().getName())
                .doesNotContain(ex.getClass().getSimpleName())
                .doesNotContain("stackTrace")
                .doesNotContain("at com.");
    }
}
