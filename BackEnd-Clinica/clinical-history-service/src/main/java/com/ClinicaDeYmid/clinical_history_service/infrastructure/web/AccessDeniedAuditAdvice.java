package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.application.access.RecordAccess;
import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.commons.web.ProblemDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccessDeniedAuditAdvice {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedAuditAdvice.class);

    private final RecordAccess access;

    AccessDeniedAuditAdvice(RecordAccess access) {
        this.access = access;
    }

    @ExceptionHandler(ClinicalException.AccessDenied.class)
    ResponseEntity<ProblemDetail> denied(ClinicalException.AccessDenied denied) {
        log.warn("Access denied: code={} actor={} patient={} action={}", denied.code(), denied.actor().uuid(), denied.patientUuid(),
                denied.action());
        access.recordDenied(denied);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetails.of(HttpStatus.FORBIDDEN, denied.code(), denied.getMessage()));
    }
}
