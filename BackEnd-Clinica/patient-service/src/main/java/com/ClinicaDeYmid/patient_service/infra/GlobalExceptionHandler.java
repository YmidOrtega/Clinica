package com.ClinicaDeYmid.patient_service.infra;

import com.ClinicaDeYmid.patient_service.infra.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
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
public class GlobalExceptionHandler {

    // Maneja errores de validación de argumentos (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validación fallida");
        response.put("message", "Errores en los campos enviados.");
        response.put("path", request.getRequestURI());

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream().map(error -> {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("field", error.getField());
            err.put("message", error.getDefaultMessage());
            return err;
        }).toList();

        response.put("errors", errors);
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja violaciones de constraints de validación (400)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validación de parámetros fallida");
        response.put("message", "Los parámetros enviados no cumplen con los requisitos.");
        response.put("path", request.getRequestURI());

        List<Map<String, String>> errors = ex.getConstraintViolations().stream().map(violation -> {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("field", violation.getPropertyPath().toString());
            err.put("message", violation.getMessage());
            return err;
        }).toList();

        response.put("errors", errors);
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja solicitudes con cuerpo inválido (400)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequestBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Solicitud mal formada");
        response.put("message", "El cuerpo de la solicitud contiene datos inválidos.");
        response.put("path", request.getRequestURI());
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja parámetros requeridos faltantes (400)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Parámetro requerido faltante");
        response.put("message", "El parámetro '" + ex.getParameterName() + "' es requerido.");
        response.put("path", request.getRequestURI());
        response.put("parameter", ex.getParameterName());
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja tipos de parámetros incorrectos (400)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Tipo de parámetro inválido");
        response.put("message", "El parámetro '" + ex.getName() + "' debe ser de tipo " + ex.getRequiredType().getSimpleName());
        response.put("path", request.getRequestURI());
        response.put("parameter", ex.getName());
        response.put("providedValue", ex.getValue());
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja métodos HTTP no permitidos (405)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.METHOD_NOT_ALLOWED.value());
        response.put("error", "Método HTTP no permitido");
        response.put("message", "El método '" + ex.getMethod() + "' no está permitido para este endpoint.");
        response.put("path", request.getRequestURI());

        Set<String> allowedMethodsAsStrings = ex.getSupportedHttpMethods().stream()
                .map(method -> method.name())
                .collect(java.util.stream.Collectors.toSet());
        response.put("allowedMethods", allowedMethodsAsStrings);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    // Maneja rutas no encontradas (404)
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Endpoint no encontrado");
        response.put("message", "No se encontró un handler para " + ex.getHttpMethod() + " " + ex.getRequestURL());
        response.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ========== EXCEPCIONES PERSONALIZADAS DE PACIENTES ==========

    // Maneja paciente ya existente (409)
    @ExceptionHandler(PatientAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handlePatientAlreadyExists(PatientAlreadyExistsException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Paciente ya existe");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("identification", ex.getIdentificationNumber());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Maneja paciente no encontrado (404)
    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePatientNotFound(PatientNotFoundException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Paciente no encontrado");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("identification", ex.getIdentificationNumber());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Maneja paciente no activo (400)
    @ExceptionHandler(PatientNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handlePatientNotActive(PatientNotActiveException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Paciente inactivo");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("patientStatus", ex.getStatus());
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja errores de actualización de paciente (400)
    @ExceptionHandler(InvalidPatientUpdateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPatientUpdate(InvalidPatientUpdateException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Actualización inválida");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("field", ex.getField());
        response.put("reason", ex.getReason());
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja búsquedas sin resultados (404)
    @ExceptionHandler(PatientSearchNoResultsException.class)
    public ResponseEntity<Map<String, Object>> handlePatientSearchNoResults(PatientSearchNoResultsException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Sin resultados de búsqueda");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("query", ex.getQuery());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Maneja parámetros de búsqueda inválidos (400)
    @ExceptionHandler(InvalidSearchParametersException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSearchParameters(InvalidSearchParametersException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Parámetros de búsqueda inválidos");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("parameter", ex.getParameter());
        response.put("value", ex.getValue());
        return ResponseEntity.badRequest().body(response);
    }

    // Maneja errores de acceso a datos (500)
    @ExceptionHandler(PatientDataAccessException.class)
    public ResponseEntity<Map<String, Object>> handlePatientDataAccess(PatientDataAccessException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error de acceso a datos");
        response.put("message", ex.getMessage());
        response.put("path", request.getRequestURI());
        response.put("operation", ex.getOperation());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // Maneja errores genéricos de base de datos (500)
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error de base de datos");
        response.put("message", "Ha ocurrido un error al acceder a la base de datos.");
        response.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // Maneja cualquier otra excepción no contemplada (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", ZonedDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error interno del servidor");
        response.put("message", "Ha ocurrido un error inesperado.");
        response.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}