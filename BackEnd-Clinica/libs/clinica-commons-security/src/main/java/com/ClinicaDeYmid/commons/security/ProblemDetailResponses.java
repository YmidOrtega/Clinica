package com.ClinicaDeYmid.commons.security;

import com.ClinicaDeYmid.commons.web.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

final class ProblemDetailResponses {

    static final String UNAUTHENTICATED_CODE = "UNAUTHENTICATED";
    static final String UNAUTHENTICATED_DETAIL = "Se requiere una autenticación válida";
    static final String STEP_UP_REQUIRED_CODE = "STEP_UP_REQUIRED";
    static final String STEP_UP_REQUIRED_DETAIL = "Confirma tu segundo factor para continuar con esta operación";
    static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";
    static final String ACCESS_DENIED_DETAIL = "No tienes permisos para realizar esta operación";

    private ProblemDetailResponses() {
    }

    static ProblemDetail unauthenticated() {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_CODE, UNAUTHENTICATED_DETAIL);
    }

    static ProblemDetail stepUpRequired(long maxAgeSeconds) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNAUTHORIZED, STEP_UP_REQUIRED_CODE, STEP_UP_REQUIRED_DETAIL);
        problem.setProperty("maxAge", maxAgeSeconds);
        return problem;
    }

    static String stepUpChallenge(long maxAgeSeconds) {
        return "Bearer error=\"insufficient_user_authentication\", error_description=\"A second factor verified in the last "
                + maxAgeSeconds + " seconds is required\", max_age=" + maxAgeSeconds;
    }

    static ProblemDetail accessDenied() {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, ACCESS_DENIED_CODE, ACCESS_DENIED_DETAIL);
    }

    static void write(HttpServletResponse response, ProblemDetail problem, ObjectMapper objectMapper) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
