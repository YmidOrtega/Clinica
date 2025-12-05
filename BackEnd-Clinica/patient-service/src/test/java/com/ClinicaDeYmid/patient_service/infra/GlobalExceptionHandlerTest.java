package com.ClinicaDeYmid.patient_service.infra;

import com.ClinicaDeYmid.patient_service.infra.exception.*;
import com.ClinicaDeYmid.patient_service.infra.exception.base.ErrorResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/v1/patients/123");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    @DisplayName("Should handle PatientNotFoundException and return 404")
    void handlePatientNotFoundException() {
        PatientNotFoundException exception = new PatientNotFoundException("123456789");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientNotFound(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PATIENT_NOT_FOUND", response.getBody().getErrorCode());
        assertEquals("123456789", response.getBody().getMetadata().get("identificationNumber"));
        assertTrue(response.getBody().getMessage().contains("123456789"));
    }

    @Test
    @DisplayName("Should handle PatientNotActiveException and return 403")
    void handlePatientNotActiveException() {
        PatientNotActiveException exception = new PatientNotActiveException("INACTIVE");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientNotActive(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PATIENT_NOT_ACTIVE", response.getBody().getErrorCode());
        assertEquals("INACTIVE", response.getBody().getMetadata().get("status"));
    }

    @Test
    @DisplayName("Should handle PatientAlreadyExistsException and return 409")
    void handlePatientAlreadyExistsException() {
        PatientAlreadyExistsException exception = new PatientAlreadyExistsException("987654321");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientAlreadyExists(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PATIENT_ALREADY_EXISTS", response.getBody().getErrorCode());
        assertEquals("987654321", response.getBody().getMetadata().get("identificationNumber"));
    }

    @Test
    @DisplayName("Should handle InvalidSearchParametersException and return 400")
    void handleInvalidSearchParametersException() {
        InvalidSearchParametersException exception = new InvalidSearchParametersException("age", "invalid");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidSearchParameters(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_SEARCH_PARAMETERS", response.getBody().getErrorCode());
        assertEquals("age", response.getBody().getMetadata().get("parameter"));
        assertEquals("invalid", response.getBody().getMetadata().get("value"));
    }

    @Test
    @DisplayName("Should handle InvalidPatientUpdateException and return 400")
    void handleInvalidPatientUpdateException() {
        InvalidPatientUpdateException exception = new InvalidPatientUpdateException("email", "Invalid format");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidPatientUpdate(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_PATIENT_UPDATE", response.getBody().getErrorCode());
        assertEquals("email", response.getBody().getMetadata().get("field"));
        assertEquals("Invalid format", response.getBody().getMetadata().get("reason"));
    }

    @Test
    @DisplayName("Should handle PatientDataAccessException and return 503")
    void handlePatientDataAccessException() {
        PatientDataAccessException exception = new PatientDataAccessException("fetch_patient", new RuntimeException("DB connection lost"));

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientDataAccess(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PATIENT_DATA_ACCESS_ERROR", response.getBody().getErrorCode());
        assertEquals("fetch_patient", response.getBody().getMetadata().get("operation"));
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException and return 404")
    void handleResourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Patient", "123", "GET_PATIENT");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleResourceNotFound(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(exception.getErrorCode(), response.getBody().getErrorCode());
        assertEquals("Patient", response.getBody().getMetadata().get("resourceType"));
        assertEquals(exception.getOperation(), response.getBody().getOperation());
    }

    @Test
    @DisplayName("Should handle BusinessException and return 422")
    void handleBusinessException() {
        BusinessException exception = new BusinessException("Cannot delete active patient", "Patient must be inactive before deletion");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleBusinessException(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BUSINESS_RULE_VIOLATION", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle ValidationException and return 400 with field errors")
    void handleValidationException() {
        Map<String, String> fieldErrors = new HashMap<>();
        fieldErrors.put("email", "Invalid email format");
        fieldErrors.put("phone", "Phone number is required");

        ValidationException exception = new ValidationException("Validation failed", fieldErrors);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getValidationErrors().size());
        assertTrue(response.getBody().getValidationErrors().stream()
                .anyMatch(e -> e.getField().equals("email")));
    }

    @Test
    @DisplayName("Should handle ConstraintViolationException and return 400")
    void handleConstraintViolationException() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);

        when(violation.getMessage()).thenReturn("must not be null");
        when(violation.getInvalidValue()).thenReturn(null);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("patient.email");

        violations.add(violation);

        ConstraintViolationException exception = new ConstraintViolationException(violations);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleConstraintViolation(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CONSTRAINT_VIOLATION", response.getBody().getErrorCode());
        assertFalse(response.getBody().getValidationErrors().isEmpty());
    }

    @Test
    @DisplayName("Should handle DuplicateResourceException and return 409")
    void handleDuplicateResourceException() {
        DuplicateResourceException exception = new DuplicateResourceException(
                "Patient", "identificationNumber", "123456789");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDuplicateResource(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(exception.getErrorCode(), response.getBody().getErrorCode());
        assertEquals("Patient", response.getBody().getMetadata().get("resourceType"));
    }

    @Test
    @DisplayName("Should handle MedicalRecordException and return 422")
    void handleMedicalRecordException() {
        MedicalRecordException exception = new MedicalRecordException(
                "Invalid medical history record", "MedicalHistory", 123L);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMedicalRecordException(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MEDICAL_RECORD_ERROR", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle CriticalAllergyException and return 412")
    void handleCriticalAllergyException() {
        CriticalAllergyException exception = new CriticalAllergyException(
                "Critical allergy detected", 123L, "Penicillin", com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity.LIFE_THREATENING);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleCriticalAllergyException(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.PRECONDITION_FAILED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CRITICAL_ALLERGY_WARNING", response.getBody().getErrorCode());
        assertEquals(123L, response.getBody().getMetadata().get("patientId"));
        assertEquals("Penicillin", response.getBody().getMetadata().get("allergen"));
        assertEquals(com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity.LIFE_THREATENING, response.getBody().getMetadata().get("severity"));
    }

    @Test
    @DisplayName("Should handle InvalidMedicalDataException and return 400")
    void handleInvalidMedicalDataException() {
        Map<String, String> invalidFields = new HashMap<>();
        invalidFields.put("bloodPressure", "Invalid format");
        invalidFields.put("temperature", "Out of range");

        InvalidMedicalDataException exception = new InvalidMedicalDataException(
                "Invalid vital signs data", "VitalSigns", invalidFields);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidMedicalData(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_MEDICAL_DATA", response.getBody().getErrorCode());
        assertEquals(2, response.getBody().getValidationErrors().size());
        assertEquals("VitalSigns", response.getBody().getMetadata().get("dataType"));
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException and return 409")
    void handleDataIntegrityViolationException() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("Duplicate entry for key");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataIntegrityViolation(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DATA_INTEGRITY_VIOLATION", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle HttpMessageNotReadableException and return 400")
    void handleHttpMessageNotReadableException() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleHttpMessageNotReadable(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MALFORMED_JSON", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle HttpRequestMethodNotSupportedException and return 405")
    void handleHttpRequestMethodNotSupportedException() {
        HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodNotSupported(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle HttpMediaTypeNotSupportedException and return 415")
    void handleHttpMediaTypeNotSupportedException() {
        HttpMediaTypeNotSupportedException exception = mock(HttpMediaTypeNotSupportedException.class);
        when(exception.getContentType()).thenReturn(org.springframework.http.MediaType.APPLICATION_XML);
        when(exception.getSupportedMediaTypes()).thenReturn(List.of(org.springframework.http.MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMediaTypeNotSupported(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle MissingServletRequestParameterException and return 400")
    void handleMissingServletRequestParameterException() {
        MissingServletRequestParameterException exception = new MissingServletRequestParameterException("id", "String");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMissingServletRequestParameter(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MISSING_PARAMETER", response.getBody().getErrorCode());
        assertEquals("id", response.getBody().getMetadata().get("parameterName"));
    }

    @Test
    @DisplayName("Should handle MethodArgumentTypeMismatchException and return 400")
    void handleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("id");
        when(exception.getValue()).thenReturn("abc");
        when(exception.getRequiredType()).thenReturn((Class) Long.class);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodArgumentTypeMismatch(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TYPE_MISMATCH", response.getBody().getErrorCode());
        assertEquals("id", response.getBody().getMetadata().get("parameterName"));
    }


    @Test
    @DisplayName("Should handle PatientSearchNoResultsException and return 404")
    void handlePatientSearchNoResultsException() {
        PatientSearchNoResultsException exception = new PatientSearchNoResultsException("name=John");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientSearchNoResults(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PATIENT_SEARCH_NO_RESULTS", response.getBody().getErrorCode());
        assertEquals("name=John", response.getBody().getMetadata().get("searchCriteria"));
    }

    @Test
    @DisplayName("Should handle generic Exception and return 500")
    void handleGenericException() {
        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception, request);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getErrorCode());
        assertNotNull(response.getBody().getTraceId());
    }

    @Test
    @DisplayName("All error responses should have traceId")
    void allResponsesShouldHaveTraceId() {
        PatientNotFoundException exception = new PatientNotFoundException("123");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientNotFound(exception, request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getTraceId());
        assertFalse(response.getBody().getTraceId().isEmpty());
    }

    @Test
    @DisplayName("All error responses should have timestamp")
    void allResponsesShouldHaveTimestamp() {
        PatientNotFoundException exception = new PatientNotFoundException("123");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientNotFound(exception, request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    @DisplayName("All error responses should have path and method")
    void allResponsesShouldHavePathAndMethod() {
        PatientNotFoundException exception = new PatientNotFoundException("123");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientNotFound(exception, request);

        assertNotNull(response.getBody());
        assertEquals("/api/v1/patients/123", response.getBody().getPath());
        assertEquals("GET", response.getBody().getMethod());
    }

    @Test
    @DisplayName("All error responses should have userMessage")
    void allResponsesShouldHaveUserMessage() {
        PatientNotFoundException exception = new PatientNotFoundException("123");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePatientNotFound(exception, request);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getUserMessage());
        assertFalse(response.getBody().getUserMessage().isEmpty());
    }

}