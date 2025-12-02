package com.ClinicaDeYmid.auth_service.infra.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.UnexpectedTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String errorCode,
            String message,
            String userMessage,
            String path,
            String method,
            String traceId,
            Map<String, Object> metadata,
            Map<String, String> validationErrors
    ) {
        // Constructor simple para errores básicos
        public ErrorResponse(int status, String error, String message, String path, String method, String traceId) {
            this(LocalDateTime.now(), status, error, null, message, message, path, method, traceId, null, null);
        }

        // Constructor para validaciones
        public ErrorResponse(int status, String error, String message, String path, String method, String traceId,
                            Map<String, String> validationErrors) {
            this(LocalDateTime.now(), status, error, null, message, message, path, method, traceId, null, validationErrors);
        }

        // Constructor completo
        public ErrorResponse(int status, String error, String errorCode, String message, String userMessage,
                            String path, String method, String traceId, Map<String, Object> metadata) {
            this(LocalDateTime.now(), status, error, errorCode, message, userMessage, path, method, traceId, metadata, null);
        }
    }

    /**
     * Genera un ID único de rastreo para cada solicitud
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    // ========== EXCEPCIONES DE SEGURIDAD ==========

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex,
            HttpServletRequest request) {
        log.warn("Account locked - IP: {}, Path: {}", request.getRemoteAddr(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "ACCOUNT_LOCKED",
                ex.getMessage(),
                "Tu cuenta ha sido bloqueada. Por favor contacta al administrador.",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(PasswordPolicyViolationException.class)
    public ResponseEntity<ErrorResponse> handlePasswordPolicyViolation(
            PasswordPolicyViolationException ex,
            HttpServletRequest request) {
        log.warn("Password policy violation - Path: {}", request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "PASSWORD_POLICY_VIOLATION",
                ex.getMessage(),
                "La contraseña no cumple con las políticas de seguridad establecidas",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );
        return ResponseEntity.badRequest().body(errorResponse);
    }

    // ========== ERRORES DE VALIDACIÓN ==========

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        log.warn("Validation failed for request: {} {}", request.getMethod(), request.getRequestURI());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Los datos enviados no son válidos",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        log.warn("Constraint violation for request: {} {}", request.getMethod(), request.getRequestURI());

        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Violación de restricciones de validación",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                errors
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(UnexpectedTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedTypeException(
            UnexpectedTypeException ex,
            HttpServletRequest request) {
        log.error("Validation configuration error: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Error en la configuración de validación: " + ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId()
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Maneja errores de validación personalizados
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {
        log.warn("Custom validation error: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "VALIDATION_ERROR",
                ex.getMessage(),
                "Error de validación en los datos proporcionados",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    // ========== ERRORES DE AUTENTICACIÓN ==========

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        log.warn("Authentication failed for IP: {} - Path: {}", request.getRemoteAddr(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "AUTHENTICATION_FAILED",
                ex.getMessage(),
                "Credenciales inválidas. Verifica tu usuario y contraseña.",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {
        log.warn("Bad credentials for IP: {} - Path: {}", request.getRemoteAddr(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "BAD_CREDENTIALS",
                ex.getMessage(),
                "Usuario o contraseña incorrectos",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // ========== ERRORES DE AUTORIZACIÓN ==========

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Access denied for IP: {} - Path: {}", request.getRemoteAddr(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "ACCESS_DENIED",
                ex.getMessage(),
                "No tienes permisos para acceder a este recurso",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    // ========== ERRORES DE NEGOCIO ==========

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex,
            HttpServletRequest request) {
        log.warn("User not found: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "USER_NOT_FOUND",
                ex.getMessage(),
                "El usuario solicitado no fue encontrado",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(
            InvalidStatusTransitionException ex,
            HttpServletRequest request) {
        log.warn("Invalid status transition: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                "INVALID_STATUS_TRANSITION",
                ex.getMessage(),
                "La transición de estado solicitada no es válida",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request) {
        log.warn("Duplicate resource: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "DUPLICATE_RESOURCE",
                ex.getMessage(),
                "El recurso ya existe en el sistema",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    // ========== ERRORES DE TOKEN ==========

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex,
            HttpServletRequest request) {
        log.warn("Invalid token attempt from IP: {}", request.getRemoteAddr());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "INVALID_TOKEN",
                ex.getMessage(),
                "El token proporcionado no es válido",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(
            TokenExpiredException ex,
            HttpServletRequest request) {
        log.info("Token expired for request: {}", request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "TOKEN_EXPIRED",
                ex.getMessage(),
                "El token ha expirado. Por favor, solicita uno nuevo.",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(TokenAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleTokenAlreadyUsed(
            TokenAlreadyUsedException ex,
            HttpServletRequest request) {
        log.warn("Attempt to reuse token from IP: {}", request.getRemoteAddr());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "TOKEN_ALREADY_USED",
                ex.getMessage(),
                "El token ya ha sido utilizado previamente",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    // ========== ERRORES GENERALES ==========

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        log.warn("Type mismatch for parameter: {}", ex.getName());

        String requiredTypeName = Optional.ofNullable(ex.getRequiredType())
                .map(Class::getSimpleName)
                .orElse("desconocido");

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "INVALID_PARAMETER_TYPE",
                "El parámetro '" + ex.getName() + "' debe ser de tipo " + requiredTypeName,
                "Tipo de parámetro inválido en la solicitud",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                Map.of(
                        "parameter", ex.getName(),
                        "expectedType", requiredTypeName,
                        "providedValue", ex.getValue() != null ? ex.getValue().toString() : "null"
                )
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("Malformed JSON in request: {} {}", request.getMethod(), request.getRequestURI());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "MALFORMED_JSON",
                "El formato del JSON enviado no es válido",
                "El cuerpo de la solicitud contiene un JSON mal formado",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "ILLEGAL_ARGUMENT",
                ex.getMessage(),
                "Argumento inválido en la solicitud",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {
        log.error("Runtime exception occurred: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_SERVER_ERROR",
                ex.getMessage() != null ? ex.getMessage() : "Error interno del servidor",
                "Ha ocurrido un error interno. Por favor, contacta al administrador.",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        log.error("Unexpected exception: {} - Path: {}", ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "UNEXPECTED_ERROR",
                "Error inesperado en el servidor",
                "Ha ocurrido un error inesperado. Por favor, intenta nuevamente.",
                request.getRequestURI(),
                request.getMethod(),
                generateTraceId(),
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
