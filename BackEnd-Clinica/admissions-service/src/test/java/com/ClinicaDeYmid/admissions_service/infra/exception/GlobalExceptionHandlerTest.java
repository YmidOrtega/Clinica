package com.ClinicaDeYmid.admissions_service.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests for Admissions Service")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/admissions");
        when(request.getMethod()).thenReturn("POST");
    }

    @Test
    @DisplayName("Should handle ActiveAttentionExistsException and return 409 CONFLICT")
    void handleActiveAttentionExistsException() {
        ActiveAttentionExistsException ex = new ActiveAttentionExistsException(1L, 101L);
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleActiveAttentionExists(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ACTIVE_ATTENTION_EXISTS", response.getBody().getErrorCode());
        assertEquals(1L, response.getBody().getMetadata().get("patientId"));
        assertEquals(101L, response.getBody().getMetadata().get("existingAttentionId"));
    }

    @Test
    @DisplayName("Should handle AttentionAlreadyInvoicedException and return 409 CONFLICT")
    void handleAttentionAlreadyInvoicedException() {
        AttentionAlreadyInvoicedException ex = new AttentionAlreadyInvoicedException(2L, "INV-001");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleAttentionAlreadyInvoiced(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ATTENTION_ALREADY_INVOICED", response.getBody().getErrorCode());
        assertEquals(2L, response.getBody().getMetadata().get("attentionId"));
        assertEquals("INV-001", response.getBody().getMetadata().get("invoiceNumber"));
    }

    @Test
    @DisplayName("Should handle AttentionSearchException and return 400 BAD_REQUEST")
    void handleAttentionSearchException() {
        AttentionSearchException ex = new AttentionSearchException("Error searching attention", "Invalid date range");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleAttentionSearch(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ATTENTION_SEARCH_ERROR", response.getBody().getErrorCode());
        assertEquals("Invalid date range", response.getBody().getMetadata().get("searchCriteria"));
    }

    @Test
    @DisplayName("Should handle ConfigurationServiceNotActiveException and return 422 UNPROCESSABLE_ENTITY")
    void handleConfigurationServiceNotActiveException() {
        ConfigurationServiceNotActiveException ex = new ConfigurationServiceNotActiveException(5L);
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConfigurationServiceNotActive(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("CONFIGURATION_SERVICE_NOT_ACTIVE", response.getBody().getErrorCode());
        assertEquals(5L, response.getBody().getMetadata().get("configurationServiceId"));
    }

    @Test
    @DisplayName("Should handle EntityNotFoundException and return 404 NOT_FOUND")
    void handleEntityNotFoundException() {
        // Using the custom EntityNotFoundException from admissions-service infra.exception package
        com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException ex =
            new com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException("Attention with ID 10 not found");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleEntityNotFound(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("ENTITY_NOT_FOUND", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle ExternalServiceUnavailableException and return 503 SERVICE_UNAVAILABLE")
    void handleExternalServiceUnavailableException() {
        ExternalServiceUnavailableException ex = new ExternalServiceUnavailableException("Patient Service");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleExternalServiceUnavailable(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("EXTERNAL_SERVICE_UNAVAILABLE", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle ValidationException and return 400 BAD_REQUEST")
    void handleValidationException() {
        ValidationException ex = new ValidationException("PatientId cannot be null");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidation(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException and return 400 BAD_REQUEST")
    void handleMethodArgumentNotValidException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(Collections.singletonList(new FieldError("objectName", "fieldName", "defaultMessage")));

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodArgumentNotValid(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BEAN_VALIDATION_ERROR", response.getBody().getErrorCode());
        assertNotNull(response.getBody().getMetadata().get("fieldErrors"));
    }

    @Test
    @DisplayName("Should handle ConstraintViolationException and return 400 BAD_REQUEST")
    void handleConstraintViolationException() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("Size must be between 1 and 100");
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("field");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConstraintViolation(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONSTRAINT_VIOLATION", response.getBody().getErrorCode());
        assertNotNull(response.getBody().getMetadata().get("violations"));
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException and return 400 BAD_REQUEST")
    void handleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalArgument(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ILLEGAL_ARGUMENT", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle IllegalStateException and return 409 CONFLICT")
    void handleIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Attention already closed");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalState(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ILLEGAL_STATE", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle Generic Exception and return 500 INTERNAL_SERVER_ERROR")
    void handleGenericException() {
        Exception ex = new Exception("Something unexpected happened");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("UNEXPECTED_ERROR", response.getBody().getErrorCode());
    }
}