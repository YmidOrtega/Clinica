package com.ClinicaDeYmid.admissions_service.infra.exception;

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
import java.util.HashMap;
import java.util.Map;
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

    // ========== EXCEPCIONES DE DOMINIO ==========

    /**
     * Maneja excepción cuando un paciente ya tiene una atención activa
     */
    @ExceptionHandler(ActiveAttentionExistsException.class)
    public ResponseEntity<ErrorResponse> handleActiveAttentionExists(
            ActiveAttentionExistsException ex,
            HttpServletRequest request) {

        log.warn("Active attention already exists - PatientId: {}, AttentionId: {}",
                ex.getPatientId(), ex.getExistingAttentionId());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("patientId", ex.getPatientId());
        metadata.put("existingAttentionId", ex.getExistingAttentionId());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .errorCode("ACTIVE_ATTENTION_EXISTS")
                .message(ex.getMessage())
                .userMessage("El paciente ya tiene una atención activa en el sistema")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .metadata(metadata)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Maneja excepción cuando se intenta modificar una atención ya facturada
     */
    @ExceptionHandler(AttentionAlreadyInvoicedException.class)
    public ResponseEntity<ErrorResponse> handleAttentionAlreadyInvoiced(
            AttentionAlreadyInvoicedException ex,
            HttpServletRequest request) {

        log.warn("Attempt to modify invoiced attention - AttentionId: {}, InvoiceNumber: {}",
                ex.getAttentionId(), ex.getInvoiceNumber());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("attentionId", ex.getAttentionId());
        metadata.put("invoiceNumber", ex.getInvoiceNumber());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .errorCode("ATTENTION_ALREADY_INVOICED")
                .message(ex.getMessage())
                .userMessage("No se puede modificar una atención que ya ha sido facturada")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .metadata(metadata)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Maneja excepción de error en búsqueda de atenciones
     */
    @ExceptionHandler(AttentionSearchException.class)
    public ResponseEntity<ErrorResponse> handleAttentionSearch(
            AttentionSearchException ex,
            HttpServletRequest request) {

        log.warn("Attention search error - Criteria: {}", ex.getSearchCriteria());

        Map<String, Object> metadata = new HashMap<>();
        if (ex.getSearchCriteria() != null) {
            metadata.put("searchCriteria", ex.getSearchCriteria());
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("ATTENTION_SEARCH_ERROR")
                .message(ex.getMessage())
                .userMessage("Error en la búsqueda de atenciones. Verifica los criterios de búsqueda.")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .metadata(metadata.isEmpty() ? null : metadata)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Maneja excepción cuando un servicio de configuración no está activo
     */
    @ExceptionHandler(ConfigurationServiceNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleConfigurationServiceNotActive(
            ConfigurationServiceNotActiveException ex,
            HttpServletRequest request) {

        log.warn("Configuration service not active - ServiceId: {}", ex.getConfigurationServiceId());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("configurationServiceId", ex.getConfigurationServiceId());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase())
                .errorCode("CONFIGURATION_SERVICE_NOT_ACTIVE")
                .message(ex.getMessage())
                .userMessage("El servicio de configuración solicitado no está activo")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .metadata(metadata)
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Maneja excepción de entidad no encontrada
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

    /**
     * Maneja excepción de servicio externo no disponible
     */
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceUnavailable(
            ExternalServiceUnavailableException ex,
            HttpServletRequest request) {

        log.error("External service unavailable: {} - Path: {}", ex.getMessage(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .errorCode("EXTERNAL_SERVICE_UNAVAILABLE")
                .message(ex.getMessage())
                .userMessage("Servicio externo temporalmente no disponible. Por favor, intenta más tarde.")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Maneja excepción de validación personalizada
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
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
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // ========== ERRORES DE VALIDACIÓN DE BEAN VALIDATION ==========

    /**
     * Maneja errores de validación de Bean Validation (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Bean validation error - Path: {} {}", request.getMethod(), request.getRequestURI());

        Map<String, Object> metadata = new HashMap<>();
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Error de validación",
                        (existing, replacement) -> existing
                ));
        metadata.put("fieldErrors", fieldErrors);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("BEAN_VALIDATION_ERROR")
                .message("Uno o más campos contienen errores de validación")
                .userMessage("Los datos enviados no son válidos")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .metadata(metadata)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Maneja violaciones de restricciones de Bean Validation
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.warn("Constraint violation - Path: {} {}", request.getMethod(), request.getRequestURI());

        Map<String, Object> metadata = new HashMap<>();
        Map<String, String> violations = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing
                ));
        metadata.put("violations", violations);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Violación de restricciones de validación")
                .userMessage("Los datos no cumplen con las restricciones requeridas")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .metadata(metadata)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // ========== ERRORES GENERALES ==========

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

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Maneja errores de estado ilegal
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
                .message("Ha ocurrido un error inesperado en el servidor")
                .userMessage("Error inesperado. Por favor, contacta al administrador.")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
