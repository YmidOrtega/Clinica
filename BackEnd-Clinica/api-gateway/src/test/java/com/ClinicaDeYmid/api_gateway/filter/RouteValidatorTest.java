package com.ClinicaDeYmid.api_gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.*;

class RouteValidatorTest {

    private final RouteValidator routeValidator = new RouteValidator();

    @Test
    void isSecured_ShouldReturnFalse_ForOpenEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        assertFalse(routeValidator.isSecured(request));

        request = MockServerHttpRequest.get("/eureka/apps").build();
        assertFalse(routeValidator.isSecured(request));

        request = MockServerHttpRequest.get("/actuator/health").build();
        assertFalse(routeValidator.isSecured(request));
    }

    @Test
    void isSecured_ShouldReturnTrue_ForSecuredEndpoints() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/patients").build();
        assertTrue(routeValidator.isSecured(request));

        request = MockServerHttpRequest.get("/api/v1/doctors/123").build();
        assertTrue(routeValidator.isSecured(request));
    }
}
