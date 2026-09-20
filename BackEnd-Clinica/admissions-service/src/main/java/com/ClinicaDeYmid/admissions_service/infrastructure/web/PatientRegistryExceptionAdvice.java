package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.application.patient.PatientDirectoryException;
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
class PatientRegistryExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(PatientRegistryExceptionAdvice.class);

    @ExceptionHandler(PatientDirectoryException.RegistryUnavailable.class)
    ResponseEntity<ProblemDetail> unavailable(PatientDirectoryException.RegistryUnavailable ex) {
        log.error("patient-service is unavailable; no admission can be created right now");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "PATIENT_REGISTRY_UNAVAILABLE",
                        "El registro de pacientes no responde; no se puede admitir en este momento"));
    }

    @ExceptionHandler(PatientDirectoryException.CannotRegisterUnidentified.class)
    ResponseEntity<ProblemDetail> cannotRegister(PatientDirectoryException.CannotRegisterUnidentified ex) {
        log.error("patient-service could not register the unidentified patient; the admission was not created");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "UNIDENTIFIED_PATIENT_NOT_REGISTERED",
                        "No se pudo registrar al paciente sin identificar; no se creó la admisión"));
    }
}
