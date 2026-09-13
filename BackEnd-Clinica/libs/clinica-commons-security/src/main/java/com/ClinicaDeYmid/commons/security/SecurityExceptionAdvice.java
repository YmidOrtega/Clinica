package com.ClinicaDeYmid.commons.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(ProblemDetailResponses.accessDenied());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(ProblemDetailResponses.unauthenticated());
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
