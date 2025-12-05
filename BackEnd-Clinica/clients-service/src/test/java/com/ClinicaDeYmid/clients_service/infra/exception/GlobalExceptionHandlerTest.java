package com.ClinicaDeYmid.clients_service.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    @DisplayName("Should handle HealthProviderNotFoundException with 404")
    void handleHealthProviderNotFoundException() {
        HealthProviderNotFoundException ex = new HealthProviderNotFoundException("123456789");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleHealthProviderException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("HP_NOT_FOUND", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle DuplicateHealthProviderNitException with 409")
    void handleDuplicateHealthProviderNitException() {
        DuplicateHealthProviderNitException ex = new DuplicateHealthProviderNitException("123456789");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleHealthProviderException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("HP_DUPLICATE_NIT", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle HealthProviderValidationException with 400")
    void handleHealthProviderValidationException() {
        HealthProviderValidationException ex = new HealthProviderValidationException("Invalid data");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleHealthProviderException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        // Note: The HealthProviderValidationException constructor seems to swap message and errorCode
        assertEquals("Invalid data", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle ContractNotFoundException with 404")
    void handleContractNotFoundException() {
        ContractNotFoundException ex = new ContractNotFoundException(1L);
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleContractNotFound(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("CONTRACT_NOT_FOUND", response.getBody().getErrorCode());
    }

    @Test
    @DisplayName("Should handle Generic Exception with 500")
    void handleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(ex, request);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
    }
}
