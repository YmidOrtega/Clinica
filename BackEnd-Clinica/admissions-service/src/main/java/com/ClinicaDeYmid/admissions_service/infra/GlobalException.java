package com.ClinicaDeYmid.admissions_service.infra;

import com.ClinicaDeYmid.admissions_service.infra.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.ZonedDateTime;
import java.util.*;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalException {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Error de validación");
        response.put("message", "Los datos enviados no cumplen con los requisitos de validación.");
        response.put("path", request.getRequestURI());

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream().map(error -> {
    Map<String, String> err = new LinkedHashMap<>();
    err.put("field", error.getField());
    err.put("message", error.getDefaultMessage());
    Object rejected = error.getRejectedValue(); 
    err.put("rejectedValue", rejected != null ? rejected.toString() : null);
    return err;
}).toList();

        response.put("validationErrors", errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Violación de restricciones");
        response.put("message", "Los parámetros no cumplen con las restricciones definidas.");
        response.put("path", request.getRequestURI());

        List<Map<String, String>> errors = ex.getConstraintViolations().stream().map(violation -> {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("field", violation.getPropertyPath().toString());
            err.put("message", violation.getMessage());
            err.put("invalidValue", violation.getInvalidValue() != null ? violation.getInvalidValue().toString() : null);
            return err;
        }).toList();

        response.put("constraintViolations", errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequestBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Formato de solicitud inválido");
        response.put("message", "El cuerpo de la solicitud no tiene un formato JSON válido o contiene datos incorrectos.");
        response.put("path", request.getRequestURI());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Parámetro requerido faltante");
        response.put("message", "El parámetro '" + ex.getParameterName() + "' es obligatorio y no fue proporcionado.");
        response.put("path", request.getRequestURI());
        response.put("missingParameter", ex.getParameterName());
        response.put("parameterType", ex.getParameterType());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<Map<String, Object>> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("timestamp", ZonedDateTime.now());
    response.put("status", HttpStatus.BAD_REQUEST.value());
    response.put("error", "Tipo de parámetro incorrecto");
    response.put("path", request.getRequestURI());
    response.put("parameter", ex.getName());
    response.put("providedValue", ex.getValue());

    Class<?> required = ex.getRequiredType();
    String requiredTypeName = (required == null) ? "desconocido" : required.getSimpleName();
    response.put("expectedType", requiredTypeName);

    StringBuilder message = new StringBuilder("El parámetro '")
            .append(ex.getName())
            .append("' debe ser de tipo ")
            .append(requiredTypeName);

    if (required != null && required.isEnum()) {
        Object[] constants = required.getEnumConstants();
        if (constants != null && constants.length > 0) {
            String allowed = Arrays.stream(constants)
                    .map(Object::toString)
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            message.append(". Valores permitidos: [").append(allowed).append("]");
        }
    }

    response.put("message", message.toString());
    return ResponseEntity.badRequest().body(response);
}



    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.METHOD_NOT_ALLOWED.value());
        response.put("error", "Método HTTP no permitido");
        response.put("message", "El método '" + ex.getMethod() + "' no está permitido para este endpoint.");
        response.put("path", request.getRequestURI());
        response.put("method", ex.getMethod());
        response.put("supportedMethods", ex.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Endpoint no encontrado");
        response.put("message", "No se encontró el endpoint solicitado.");
        response.put("path", request.getRequestURI());
        response.put("method", ex.getHttpMethod());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }


    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Recurso no encontrado");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "ENTITY_NOT_FOUND");

        // Detectar tipo de entidad no encontrada
        String message = ex.getMessage().toLowerCase();
        if (message.contains("attention")) {
            response.put("entityType", "ATTENTION");
        } else if (message.contains("configurationservice")) {
            response.put("entityType", "CONFIGURATION_SERVICE");
        } else if (message.contains("patient")) {
            response.put("entityType", "PATIENT");
        } else if (message.contains("doctor")) {
            response.put("entityType", "DOCTOR");
        } else {
            response.put("entityType", "UNKNOWN");
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Manejo específico para atención ya facturada
    @ExceptionHandler(AttentionAlreadyInvoicedException.class)
    public ResponseEntity<Map<String, Object>> handleAttentionAlreadyInvoiced(AttentionAlreadyInvoicedException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Atención ya facturada");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "ATTENTION_ALREADY_INVOICED");
        response.put("attentionId", ex.getAttentionId());
        response.put("invoiceNumber", ex.getInvoiceNumber());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Manejo específico para configuración de servicio inactiva
    @ExceptionHandler(ConfigurationServiceNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleConfigurationServiceNotActive(ConfigurationServiceNotActiveException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Servicio de configuración inactivo");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "CONFIGURATION_SERVICE_NOT_ACTIVE");
        response.put("configurationServiceId", ex.getConfigurationServiceId());
        return ResponseEntity.badRequest().body(response);
    }

    // Manejo específico para atención activa existente
    @ExceptionHandler(ActiveAttentionExistsException.class)
    public ResponseEntity<Map<String, Object>> handleActiveAttentionExists(ActiveAttentionExistsException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Atención activa existente");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "ACTIVE_ATTENTION_EXISTS");
        response.put("patientId", ex.getPatientId());
        response.put("existingAttentionId", ex.getExistingAttentionId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Manejo específico para errores de búsqueda
    @ExceptionHandler(AttentionSearchException.class)
    public ResponseEntity<Map<String, Object>> handleAttentionSearchException(AttentionSearchException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Error en búsqueda de atenciones");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "ATTENTION_SEARCH_ERROR");
        response.put("searchCriteria", ex.getSearchCriteria());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Error de validación del negocio");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "BUSINESS_VALIDATION_ERROR");

        // Detectar si es un error de atención facturada
        if (ex.getMessage().contains("invoiced") || ex.getMessage().contains("facturada")) {
            response.put("errorCategory", "INVOICED_ATTENTION");
        } else if (ex.getMessage().contains("ConfigurationService")) {
            response.put("errorCategory", "CONFIGURATION_SERVICE");
        } else if (ex.getMessage().contains("Patient")) {
            response.put("errorCategory", "PATIENT_VALIDATION");
        } else if (ex.getMessage().contains("Doctor")) {
            response.put("errorCategory", "DOCTOR_VALIDATION");
        } else if (ex.getMessage().contains("health provider")) {
            response.put("errorCategory", "HEALTH_PROVIDER_VALIDATION");
        } else {
            response.put("errorCategory", "GENERAL_VALIDATION");
        }

        return ResponseEntity.badRequest().body(response);
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Violación de integridad de datos");
        response.put("path", request.getRequestURI());
        response.put("errorType", "DATA_INTEGRITY_VIOLATION");

        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Duplicate entry")) {
                response.put("message", "Ya existe un registro con los mismos datos únicos.");
                response.put("cause", "DUPLICATE_ENTRY");
            } else if (message.contains("foreign key constraint")) {
                response.put("message", "Violación de restricción de clave foránea. Verifique las relaciones entre entidades.");
                response.put("cause", "FOREIGN_KEY_CONSTRAINT");
            } else if (message.contains("cannot be null")) {
                response.put("message", "Campo obligatorio no puede ser nulo.");
                response.put("cause", "NULL_CONSTRAINT");
            } else {
                response.put("message", "Error de integridad de datos en la base de datos.");
                response.put("cause", "UNKNOWN_INTEGRITY_ERROR");
            }
        } else {
            response.put("message", "Error de integridad de datos.");
            response.put("cause", "UNKNOWN_INTEGRITY_ERROR");
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error de acceso a datos");
        response.put("message", "Se produjo un error al acceder a la base de datos. Inténtelo nuevamente.");
        response.put("path", request.getRequestURI());
        response.put("errorType", "DATABASE_ACCESS_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }


    @ExceptionHandler(org.springframework.transaction.TransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionException(org.springframework.transaction.TransactionException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error de transacción");
        response.put("message", "Se produjo un error durante la transacción. La operación no se completó.");
        response.put("path", request.getRequestURI());
        response.put("errorType", "TRANSACTION_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }



    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", "Acceso denegado");
        response.put("message", "No tiene permisos suficientes para realizar esta operación.");
        response.put("path", request.getRequestURI());
        response.put("errorType", "ACCESS_DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", "Error de autenticación");
        response.put("message", "Las credenciales proporcionadas no son válidas.");
        response.put("path", request.getRequestURI());
        response.put("errorType", "AUTHENTICATION_ERROR");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error interno del servidor");
        response.put("message", "Se produjo un error inesperado. Por favor, contacte al administrador del sistema.");
        response.put("path", request.getRequestURI());
        response.put("errorType", "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleExternalServiceUnavailable(ExternalServiceUnavailableException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_GATEWAY.value());
        response.put("error", "Servicio externo no disponible");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("errorType", "EXTERNAL_SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

}