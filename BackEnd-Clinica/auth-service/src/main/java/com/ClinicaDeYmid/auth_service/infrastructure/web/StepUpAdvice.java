package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.StepUpRequired;
import com.ClinicaDeYmid.commons.web.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class StepUpAdvice {

    @ExceptionHandler(StepUpRequired.class)
    ResponseEntity<ProblemDetail> stepUpRequired(StepUpRequired required) {
        long maxAge = required.maxAge().toSeconds();
        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                "Confirma tu segundo factor para continuar con esta operación");
        problem.setProperty("maxAge", maxAge);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_user_authentication\", "
                        + "error_description=\"A second factor verified in the last " + maxAge + " seconds is required\", max_age=" + maxAge)
                .body(problem);
    }
}
