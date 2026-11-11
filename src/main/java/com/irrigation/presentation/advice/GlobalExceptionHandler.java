package com.irrigation.presentation.advice;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.irrigation.application.validation.FieldViolation;
import com.irrigation.application.validation.ValidationFailedException;
import com.irrigation.domain.calculation.exception.DuplicateMethodologyException;
import com.irrigation.domain.calculation.exception.InvalidCalculationInputException;
import com.irrigation.domain.calculation.exception.MethodologyNotAvailableException;
import com.irrigation.domain.exception.BusinessRuleException;
import com.irrigation.domain.exception.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Converte toda exceção em uma resposta RFC 7807 ({@code application/problem+json}) (Req 6.10, 7).
 *
 * <ul>
 *   <li>400 — entrada inválida, com a extensão {@code violations[]} (campo + mensagem)</li>
 *   <li>404 — recurso inexistente</li>
 *   <li>422 — violação de regra de negócio</li>
 *   <li>500 — erro inesperado, com detalhe genérico (sem stack trace nem detalhes internos)</li>
 * </ul>
 *
 * <p>Toda exceção é registrada em log com tipo, mensagem e stack trace (Req 7.5).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String TITLE_INVALID = "Dados inválidos";
    static final String TITLE_NOT_FOUND = "Recurso não encontrado";
    static final String TITLE_BUSINESS_RULE = "Violação de regra de negócio";
    static final String TITLE_UNEXPECTED = "Erro interno do servidor";
    static final String DETAIL_UNEXPECTED = "Ocorreu um erro inesperado. Tente novamente mais tarde.";

    // ------------------------------------------------------------------ 400

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<Object> handleValidationFailed(ValidationFailedException ex) {
        return problem(ex, HttpStatus.BAD_REQUEST, TITLE_INVALID,
                "A requisição contém dados inválidos.", ex.getViolations());
    }

    @ExceptionHandler(InvalidCalculationInputException.class)
    public ResponseEntity<Object> handleInvalidCalculationInput(InvalidCalculationInputException ex) {
        List<FieldViolation> violations = ex.getInvalidParameters().stream()
                .map(p -> new FieldViolation(p, "ausente ou fora do intervalo válido"))
                .toList();
        return problem(ex, HttpStatus.BAD_REQUEST, TITLE_INVALID,
                "Os parâmetros de cálculo informados são inválidos.", violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(lastSegment(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return problem(ex, HttpStatus.BAD_REQUEST, TITLE_INVALID,
                "A requisição contém dados inválidos.", violations);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new java.util.ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> violations.add(new FieldViolation(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> violations.add(new FieldViolation(e.getObjectName(), e.getDefaultMessage())));
        return problem(ex, HttpStatus.BAD_REQUEST, TITLE_INVALID,
                "A requisição contém dados inválidos.", violations);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = List.of();
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            violations = List.of(new FieldViolation(jsonPath(mapping), "valor inválido ou de tipo incorreto"));
        }
        // Não repassa a mensagem do Jackson: ela expõe detalhes internos de classes.
        return problem(ex, HttpStatus.BAD_REQUEST, TITLE_INVALID,
                "O corpo da requisição está ausente, malformado ou contém valores inválidos.", violations);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = "parâmetro";
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            field = mismatch.getName();
        } else if (ex.getPropertyName() != null) {
            field = ex.getPropertyName();
        }
        return problem(ex, HttpStatus.BAD_REQUEST, TITLE_INVALID,
                "A requisição contém um parâmetro com formato inválido.",
                List.of(new FieldViolation(field, "valor com formato inválido")));
    }

    // ------------------------------------------------------------------ 404

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(EntityNotFoundException ex) {
        return problem(ex, HttpStatus.NOT_FOUND, TITLE_NOT_FOUND, ex.getMessage(), null);
    }

    // ------------------------------------------------------------------ 422

    @ExceptionHandler({
            BusinessRuleException.class,
            MethodologyNotAvailableException.class,
            DuplicateMethodologyException.class})
    public ResponseEntity<Object> handleBusinessRule(RuntimeException ex) {
        return problem(ex, HttpStatus.UNPROCESSABLE_ENTITY, TITLE_BUSINESS_RULE, ex.getMessage(), null);
    }

    // ------------------------------------------------------------------ 500

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, TITLE_UNEXPECTED, DETAIL_UNEXPECTED, null);
    }

    // ------------------------------------------------------- demais erros do Spring (405, 415, ...)

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        logException(ex, statusCode);
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    // ------------------------------------------------------------------ auxiliares

    private ResponseEntity<Object> problem(
            Exception ex, HttpStatusCode status, String title, String detail, @Nullable List<FieldViolation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (violations != null && !violations.isEmpty()) {
            problem.setProperty("violations", violations);
        }
        logException(ex, status);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private void logException(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            log.error("Erro inesperado [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        } else {
            log.warn("Requisição rejeitada [{}] ({}): {}", ex.getClass().getName(), status.value(), ex.getMessage(), ex);
        }
    }

    private static String lastSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String jsonPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
