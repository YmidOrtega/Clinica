package com.ClinicaDeYmid.api_gateway.infrastructure.security;

import com.ClinicaDeYmid.commons.web.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.util.Map;

final class ProblemResponses {

    private ProblemResponses() {
    }

    static void write(HttpServletResponse response, ObjectMapper json, HttpStatus status, String code, String detail, Map<String, Object> properties)
            throws IOException {
        ProblemDetail problem = ProblemDetails.of(status, code, detail);
        properties.forEach(problem::setProperty);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
