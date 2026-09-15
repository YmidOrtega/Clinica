package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.login.LoginException;
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
class LoginExceptionAdvice {

    @ExceptionHandler(LoginException.class)
    ResponseEntity<ProblemDetail> loginFailed(LoginException failure) {
        HttpStatus status = switch (failure) {
            case LoginException.InvalidCredentials invalid -> HttpStatus.UNAUTHORIZED;
            case LoginException.TooManyAttempts tooMany -> HttpStatus.TOO_MANY_REQUESTS;
            case LoginException.AccountLocked locked -> HttpStatus.LOCKED;
            case LoginException.PasswordChangeNotPending notPending -> HttpStatus.CONFLICT;
            case LoginException.PasswordReused reused -> HttpStatus.BAD_REQUEST;
            case LoginException.InvalidLink invalidLink -> HttpStatus.BAD_REQUEST;
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (failure instanceof LoginException.TooManyAttempts tooMany) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, (tooMany.retryAfter().toMillis() + 999) / 1000)));
        }
        return response.body(ProblemDetails.of(status, failure.code(), failure.getMessage()));
    }
}
