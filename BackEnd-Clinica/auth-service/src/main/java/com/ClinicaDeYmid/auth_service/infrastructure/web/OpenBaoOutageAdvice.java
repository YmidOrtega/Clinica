package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
import com.ClinicaDeYmid.commons.web.ProblemDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class OpenBaoOutageAdvice {

    private static final Logger log = LoggerFactory.getLogger(OpenBaoOutageAdvice.class);

    @ExceptionHandler(OpenBaoUnavailableException.class)
    ResponseEntity<ProblemDetail> unavailable(OpenBaoUnavailableException outage) {
        log.warn("OpenBao is unavailable: {}", outage.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_KEYS_UNAVAILABLE",
                        "Las claves de seguridad no están disponibles en este momento; intenta de nuevo en unos segundos"));
    }
}
