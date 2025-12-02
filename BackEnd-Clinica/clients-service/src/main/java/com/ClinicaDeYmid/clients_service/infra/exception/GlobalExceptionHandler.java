package com.ClinicaDeYmid.clients_service.infra.exception;

import com.ClinicaDeYmid.clients_service.infra.exception.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja todas las excepciones de HealthProvider
     */
    @ExceptionHandler(HealthProviderException.class)
    public ResponseEntity<ErrorResponse> handleHealthProviderException(
            HealthProviderException ex, HttpServletRequest request) {

        log.warn("HealthProvider error [{}]: {}", ex.getErrorCode(), ex.getMessage());

        HttpStatus status = determineHttpStatus(ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .operation(ex.getOperation())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(status).body(error);
    }

    /**
     * Determina el HttpStatus basado en el tipo de excepción
     */
    private HttpStatus determineHttpStatus(HealthProviderException ex) {
        // Conflictos (409)
        if (ex instanceof DuplicateHealthProviderNitException ||
                ex instanceof DuplicateContractNumberException ||
                ex instanceof UpdateHealthProviderNitConflictException ||
                ex instanceof HealthProviderAlreadyActiveException ||
                ex instanceof HealthProviderAlreadyInactiveException ||
                ex instanceof HealthProviderWithActiveContractsException ||
                ex instanceof HealthProviderDeletionRestrictedException) {
            return HttpStatus.CONFLICT;
        }

        // No encontrado (404)
        if (ex instanceof HealthProviderNotFoundException ||
                ex instanceof HealthProviderNotFoundForStatusException ||
                ex instanceof NoHealthProvidersFoundException) {
            return HttpStatus.NOT_FOUND;
        }

        // Bad Request (400)
        if (ex instanceof HealthProviderValidationException ||
                ex instanceof HealthProviderNotActiveException) {
            return HttpStatus.BAD_REQUEST;
        }

        // Errores de servidor (500/503)
        if (ex instanceof HealthProviderDataAccessException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Maneja ContractNotFoundException
     */
    @ExceptionHandler(ContractNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleContractNotFound(
            ContractNotFoundException ex, HttpServletRequest request) {

        log.warn("Contract not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .errorCode("CONTRACT_NOT_FOUND")
                .message(ex.getMessage())
                .userMessage("El contrato solicitado no fue encontrado")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Maneja ContractValidationException
     */
    @ExceptionHandler(ContractValidationException.class)
    public ResponseEntity<ErrorResponse> handleContractValidation(
            ContractValidationException ex, HttpServletRequest request) {

        log.warn("Contract validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("CONTRACT_VALIDATION_ERROR")
                .message(ex.getMessage())
                .userMessage("Error de validación en los datos del contrato")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja ContractAlreadyActiveException
     */
    @ExceptionHandler(ContractAlreadyActiveException.class)
    public ResponseEntity<ErrorResponse> handleContractAlreadyActive(
            ContractAlreadyActiveException ex, HttpServletRequest request) {

        log.warn("Contract already active: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .errorCode("CONTRACT_ALREADY_ACTIVE")
                .message(ex.getMessage())
                .userMessage("El contrato ya se encuentra activo")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Maneja ContractAlreadyInactiveException
     */
    @ExceptionHandler(ContractAlreadyInactiveException.class)
    public ResponseEntity<ErrorResponse> handleContractAlreadyInactive(
            ContractAlreadyInactiveException ex, HttpServletRequest request) {

        log.warn("Contract already inactive: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .errorCode("CONTRACT_ALREADY_INACTIVE")
                .message(ex.getMessage())
                .userMessage("El contrato ya se encuentra inactivo")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Maneja ContractDeletionRestrictedException
     */
    @ExceptionHandler(ContractDeletionRestrictedException.class)
    public ResponseEntity<ErrorResponse> handleContractDeletionRestricted(
            ContractDeletionRestrictedException ex, HttpServletRequest request) {

        log.warn("Contract deletion restricted: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .errorCode("CONTRACT_DELETION_RESTRICTED")
                .message(ex.getMessage())
                .userMessage("No se puede eliminar el contrato debido a restricciones de negocio")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Maneja ContractDataAccessException
     */
    @ExceptionHandler(ContractDataAccessException.class)
    public ResponseEntity<ErrorResponse> handleContractDataAccess(
            ContractDataAccessException ex, HttpServletRequest request) {

        log.error("Contract data access error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .errorCode("CONTRACT_DATA_ACCESS_ERROR")
                .message("Error al acceder a los datos del contrato")
                .userMessage("No se pudo procesar la solicitud. Por favor intente nuevamente")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Maneja errores de validación de Bean Validation (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.warn("Validation error: {}", ex.getMessage());

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
                .error("Validation Error")
                .errorCode("VALIDATION_ERROR")
                .message("Uno o más campos contienen errores de validación")
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
            ConstraintViolationException ex, HttpServletRequest request) {

        log.warn("Constraint violation: {}", ex.getMessage());

        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Violación de restricciones de validación")
                .errors(errors)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja errores de entidad no encontrada
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {

        log.warn("Entity not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .errorCode("ENTITY_NOT_FOUND")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Maneja errores de integridad de datos
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.error("Data integrity violation: {}", ex.getMessage());

        String message = "Error de integridad de datos";
        String userMessage = "Los datos no pudieron ser guardados debido a restricciones de la base de datos";

        if (ex.getMessage() != null && ex.getMessage().contains("Duplicate entry")) {
            message = "Registro duplicado";
            userMessage = "Ya existe un registro con estos datos";
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Data Integrity Violation")
                .errorCode("DATA_INTEGRITY_VIOLATION")
                .message(message)
                .userMessage(userMessage)
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
            IllegalArgumentException ex, HttpServletRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("ILLEGAL_ARGUMENT")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja errores de estado ilegal
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .errorCode("ILLEGAL_STATE")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Maneja errores de parámetros faltantes
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing parameter: {}", ex.getParameterName());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("MISSING_PARAMETER")
                .message(String.format("El parámetro '%s' es requerido", ex.getParameterName()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja errores de tipo de argumento incorrecto
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.warn("Type mismatch for parameter: {}", ex.getName());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("TYPE_MISMATCH")
                .message(String.format("El parámetro '%s' tiene un tipo inválido", ex.getName()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja errores de método HTTP no soportado
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Method not supported: {}", ex.getMethod());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .errorCode("METHOD_NOT_ALLOWED")
                .message(String.format("El método '%s' no está soportado para este endpoint", ex.getMethod()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    /**
     * Maneja errores de tipo de media no soportado
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Media type not supported: {}", ex.getContentType());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error("Unsupported Media Type")
                .errorCode("UNSUPPORTED_MEDIA_TYPE")
                .message("Tipo de contenido no soportado")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
    }

    /**
     * Maneja errores de mensaje HTTP no legible
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Message not readable: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode("MESSAGE_NOT_READABLE")
                .message("El cuerpo de la solicitud no es válido o está mal formado")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Maneja cualquier otra excepción no capturada
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .errorCode("INTERNAL_ERROR")
                .message("Ha ocurrido un error inesperado")
                .userMessage("Por favor contacte al administrador del sistema")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Genera un ID de traza único para debugging
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}