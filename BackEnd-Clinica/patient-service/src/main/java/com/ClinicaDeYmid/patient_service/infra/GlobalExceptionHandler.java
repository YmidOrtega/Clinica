package com.ClinicaDeYmid.patient_service.infra;

import com.ClinicaDeYmid.patient_service.infra.exception.*;
import com.ClinicaDeYmid.patient_service.infra.exception.base.BaseException;
import com.ClinicaDeYmid.patient_service.infra.exception.base.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Maneja ResourceNotFoundException
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("El recurso solicitado no fue encontrado")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .metadata(buildMetadata(ex))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Maneja BusinessException
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("Business rule violation: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("No se puede completar la operación debido a una regla de negocio")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .metadata(Map.of("businessRule", ex.getBusinessRule() != null ? ex.getBusinessRule() : "N/A"))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * Maneja ValidationException
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {

        log.warn("Validation error: {}", ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = ex.getFieldErrors().entrySet().stream()
                .map(entry -> ErrorResponse.ValidationError.builder()
                        .field(entry.getKey())
                        .message(entry.getValue())
                        .code("INVALID_" + entry.getKey().toUpperCase())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("Los datos proporcionados no son válidos")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .validationErrors(validationErrors)
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja MethodArgumentNotValidException (validaciones de @Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.warn("Validation failed: {}", ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.ValidationError.builder()
                        .field(error.getField())
                        .rejectedValue(error.getRejectedValue())
                        .message(error.getDefaultMessage())
                        .code(error.getCode())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("VALIDATION_FAILED")
                .message("Error de validación en los datos de entrada")
                .userMessage("Por favor revise los datos ingresados")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .validationErrors(validationErrors)
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja ConstraintViolationException
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.warn("Constraint violation: {}", ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ErrorResponse.ValidationError.builder()
                        .field(getFieldName(violation))
                        .rejectedValue(violation.getInvalidValue())
                        .message(violation.getMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Violación de restricciones en los datos")
                .userMessage("Los datos no cumplen con las restricciones requeridas")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .validationErrors(validationErrors)
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja DuplicateResourceException
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request) {

        log.warn("Duplicate resource: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("El recurso ya existe en el sistema")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .metadata(buildMetadata(ex))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Maneja MedicalRecordException
     */
    @ExceptionHandler(MedicalRecordException.class)
    public ResponseEntity<ErrorResponse> handleMedicalRecordException(
            MedicalRecordException ex,
            HttpServletRequest request) {

        log.error("Medical record error: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("Error al procesar el registro médico")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .metadata(buildMetadata(ex))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * Maneja CriticalAllergyException
     */
    @ExceptionHandler(CriticalAllergyException.class)
    public ResponseEntity<ErrorResponse> handleCriticalAllergyException(
            CriticalAllergyException ex,
            HttpServletRequest request) {

        log.error("Critical allergy warning: {}", ex.getMessage());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("patientId", ex.getPatientId());
        metadata.put("allergen", ex.getAllergen());
        metadata.put("severity", ex.getSeverity());
        metadata.put("alertLevel", "CRITICAL");

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.PRECONDITION_FAILED.value())
                .error("Critical Allergy Alert")
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("ALERTA: Alergia crítica detectada - Se requiere atención inmediata")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .metadata(metadata)
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(errorResponse);
    }

    /**
     * Maneja InvalidMedicalDataException
     */
    @ExceptionHandler(InvalidMedicalDataException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMedicalData(
            InvalidMedicalDataException ex,
            HttpServletRequest request) {

        log.warn("Invalid medical data: {}", ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = ex.getInvalidFields().entrySet().stream()
                .map(entry -> ErrorResponse.ValidationError.builder()
                        .field(entry.getKey())
                        .message(entry.getValue())
                        .code("INVALID_MEDICAL_DATA")
                        .build())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .userMessage("Los datos médicos proporcionados no son válidos")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .operation(ex.getOperation())
                .validationErrors(validationErrors)
                .metadata(Map.of("dataType", ex.getDataType()))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja DataIntegrityViolationException
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.error("Data integrity violation: {}", ex.getMessage());

        String message = "Error de integridad de datos";
        String userMessage = "Los datos no pudieron ser guardados debido a restricciones de la base de datos";

        if (ex.getMessage().contains("Duplicate entry")) {
            message = "Registro duplicado";
            userMessage = "Ya existe un registro con estos datos";
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .errorCode("DATA_INTEGRITY_VIOLATION")
                .message(message)
                .userMessage(userMessage)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Maneja HttpMessageNotReadableException
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed JSON request: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("MALFORMED_JSON")
                .message("El cuerpo de la petición no es válido")
                .userMessage("El formato de los datos enviados no es correcto")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja HttpRequestMethodNotSupportedException
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("Method not supported: {} for {}", ex.getMethod(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase())
                .errorCode("METHOD_NOT_ALLOWED")
                .message(String.format("Método %s no está soportado para este endpoint", ex.getMethod()))
                .userMessage("El método HTTP utilizado no es válido para esta operación")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .metadata(Map.of(
                        "supportedMethods", ex.getSupportedHttpMethods() != null ?
                                ex.getSupportedHttpMethods().toString() : "N/A"
                ))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    /**
     * Maneja HttpMediaTypeNotSupportedException
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("Media type not supported: {}", ex.getContentType());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase())
                .errorCode("UNSUPPORTED_MEDIA_TYPE")
                .message(String.format("Tipo de contenido %s no soportado", ex.getContentType()))
                .userMessage("El formato de contenido enviado no es válido")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .metadata(Map.of(
                        "supportedMediaTypes", ex.getSupportedMediaTypes().toString()
                ))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
    }

    /**
     * Maneja MissingServletRequestParameterException
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing request parameter: {}", ex.getParameterName());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("MISSING_PARAMETER")
                .message(String.format("Parámetro requerido '%s' no está presente", ex.getParameterName()))
                .userMessage("Falta un parámetro requerido en la petición")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .metadata(Map.of(
                        "parameterName", ex.getParameterName(),
                        "parameterType", ex.getParameterType()
                ))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja MethodArgumentTypeMismatchException
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn("Type mismatch for parameter: {}", ex.getName());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("TYPE_MISMATCH")
                .message(String.format("El parámetro '%s' debe ser de tipo %s",
                        ex.getName(),
                        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"))
                .userMessage("El tipo de dato de un parámetro no es válido")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .metadata(Map.of(
                        "parameterName", ex.getName(),
                        "providedValue", ex.getValue() != null ? ex.getValue().toString() : "null",
                        "requiredType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
                ))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Maneja NoHandlerFoundException
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {

        log.warn("No handler found for: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .errorCode("ENDPOINT_NOT_FOUND")
                .message(String.format("No se encontró endpoint para %s %s", ex.getHttpMethod(), ex.getRequestURL()))
                .userMessage("La ruta solicitada no existe")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Maneja excepciones genéricas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("Ha ocurrido un error inesperado")
                .userMessage("Lo sentimos, algo salió mal. Por favor intente nuevamente más tarde")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Construye metadata desde BaseException
     */
    private Map<String, Object> buildMetadata(BaseException ex) {
        Map<String, Object> metadata = new HashMap<>();

        if (ex instanceof ResourceNotFoundException) {
            ResourceNotFoundException rnfEx = (ResourceNotFoundException) ex;
            metadata.put("resourceType", rnfEx.getResourceType());
            metadata.put("resourceId", rnfEx.getResourceId());
        } else if (ex instanceof DuplicateResourceException) {
            DuplicateResourceException drEx = (DuplicateResourceException) ex;
            metadata.put("resourceType", drEx.getResourceType());
            metadata.put("duplicateField", drEx.getDuplicateField());
            metadata.put("duplicateValue", drEx.getDuplicateValue());
        } else if (ex instanceof MedicalRecordException) {
            MedicalRecordException mrEx = (MedicalRecordException) ex;
            metadata.put("recordType", mrEx.getRecordType());
            metadata.put("patientId", mrEx.getPatientId());
        }

        return metadata;
    }

    /**
     * Maneja PatientSearchNoResultsException
     */
    @ExceptionHandler(PatientSearchNoResultsException.class)
    public ResponseEntity<ErrorResponse> handlePatientSearchNoResults(
            PatientSearchNoResultsException ex,
            HttpServletRequest request) {

        log.info("No results found for: {}", ex.getSearchCriteria());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .errorCode("PATIENT_SEARCH_NO_RESULTS")
                .message(ex.getMessage())
                .userMessage("La búsqueda no arrojó resultados")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .metadata(Map.of("searchCriteria", ex.getSearchCriteria()))
                .traceId(generateTraceId())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Extrae el nombre del campo de una ConstraintViolation
     */
    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String[] parts = propertyPath.split("\\.");
        return parts[parts.length - 1];
    }

    /**
     * Genera un ID de traza único para debugging
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}