package com.ClinicaDeYmid.suppliers_service.infra.exception;

import com.ClinicaDeYmid.suppliers_service.module.service.validation.ValidationException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Genera un ID único de rastreo para cada solicitud
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    // ========== ERRORES DE VALIDACIÓN ==========

    /**
     * Maneja errores de validación personalizada
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {

        log.warn("Validation error: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("VALIDATION_ERROR")
                .message(ex.getMessage())
                .userMessage("Error de validación en los datos proporcionados")
                .errors(ex.getErrors())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja errores de validación de Bean Validation (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Method argument validation error - Path: {} {}", request.getMethod(), request.getRequestURI());

        List<String> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String fieldName = ((FieldError) error).getField();
                    String message = error.getDefaultMessage();
                    return String.format("%s: %s", fieldName, message);
                })
                .collect(Collectors.toList());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("BEAN_VALIDATION_ERROR")
                .message("Uno o más campos contienen errores de validación")
                .userMessage("Los datos enviados no son válidos")
                .errors(errors)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja violaciones de restricciones de Bean Validation
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.warn("Constraint violation - Path: {} {}", request.getMethod(), request.getRequestURI());

        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Violación de restricciones de validación")
                .userMessage("Los datos no cumplen con las restricciones requeridas")
                .errors(errors)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== ERRORES DE ENTIDAD ==========

    /**
     * Maneja errores de entidad no encontrada
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Entity not found: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .errorCode("ENTITY_NOT_FOUND")
                .message(ex.getMessage())
                .userMessage("El recurso solicitado no fue encontrado")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ========== ERRORES DE LÓGICA DE NEGOCIO ==========

    /**
     * Maneja errores de estado ilegal (lógica de negocio)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        log.warn("Illegal state: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .errorCode("ILLEGAL_STATE")
                .message(ex.getMessage())
                .userMessage("Operación no permitida en el estado actual del recurso")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Maneja errores de argumento ilegal
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("ILLEGAL_ARGUMENT")
                .message(ex.getMessage())
                .userMessage("Argumento inválido en la solicitud")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== ERRORES GENERALES ==========

    /**
     * Maneja cualquier otra excepción no capturada
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred - Path: {} {} - Error: {}",
                  request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .errorCode("UNEXPECTED_ERROR")
                .message("Ha ocurrido un error inesperado. Por favor contacte al administrador.")
                .userMessage("Error inesperado en el servidor. Por favor, intenta nuevamente.")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}