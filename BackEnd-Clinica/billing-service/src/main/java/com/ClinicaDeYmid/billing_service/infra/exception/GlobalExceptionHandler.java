package com.ClinicaDeYmid.billing_service.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            BillingConfigurationNotFoundException.class,
            TaxConfigurationNotFoundException.class,
            DianResolutionNotFoundException.class,
            PriceManualNotFoundException.class,
            PriceManualItemNotFoundException.class,
            ClientPriceOverrideNotFoundException.class,
            SaleOrderNotFoundException.class,
            SaleOrderItemNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler({
            BillingConfigurationAlreadyActiveException.class,
            DuplicateTaxCodeException.class,
            DuplicateResolutionNumberException.class,
            DuplicatePriceManualCodeException.class,
            PriceNotResolvedException.class,
            AttentionAlreadyHasDraftException.class,
            SaleOrderNotEditableException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest req) {
        log.warn("Conflict: {}", ex.getMessage());
        String message = ex instanceof DataIntegrityViolationException
                ? "Violación de restricción de integridad en la base de datos."
                : ex.getMessage();
        return build(HttpStatus.CONFLICT, message, req, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        log.warn("Illegal state: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        f -> f.getField(),
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "Valor inválido",
                        (a, b) -> a
                ));
        log.warn("Validation failed: {}", errors);
        return build(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.", req, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage(),
                        (a, b) -> a
                ));
        log.warn("Constraint violation: {}", errors);
        return build(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.", req, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String message = "Valor inválido para el parámetro '" + ex.getName() + "': " + ex.getValue();
        log.warn("Type mismatch: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error at {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor.", req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                 HttpServletRequest req,
                                                 Map<String, String> validationErrors) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                req.getMethod(),
                UUID.randomUUID().toString(),
                validationErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}
