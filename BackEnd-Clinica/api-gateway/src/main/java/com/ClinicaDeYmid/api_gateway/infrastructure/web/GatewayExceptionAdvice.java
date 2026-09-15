package com.ClinicaDeYmid.api_gateway.infrastructure.web;

import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.AuthUnavailableException;
import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.SessionExpiredException;
import com.ClinicaDeYmid.commons.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GatewayExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionAdvice.class);

    @ExceptionHandler(SessionExpiredException.class)
    ResponseEntity<ProblemDetail> sessionExpired(SessionExpiredException expired, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "La sesión terminó; inicia sesión de nuevo");
        problem.setProperty("loginUrl", "/bff/login");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AuthUnavailableException.class)
    ResponseEntity<ProblemDetail> authUnavailable(AuthUnavailableException unavailable) {
        log.warn("auth-service did not renew the access token: {}", unavailable.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "No fue posible validar la sesión; intenta de nuevo en un momento"));
    }

    @ExceptionHandler({ResourceAccessException.class, ConnectException.class})
    ResponseEntity<ProblemDetail> serviceUnavailable(Exception unreachable, HttpServletRequest request) {
        log.warn("Upstream unavailable for {} {}: {}", request.getMethod(), request.getRequestURI(), unreachable.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "El servicio no está disponible en este momento"));
    }
}
