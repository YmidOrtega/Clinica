package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.infrastructure.encryption.EncryptedContentUnreadableException;
import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
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
class EncryptionExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(EncryptionExceptionAdvice.class);

    @ExceptionHandler(EncryptedContentUnreadableException.class)
    ResponseEntity<ProblemDetail> unreadable(EncryptedContentUnreadableException ex) {
        log.error("Clinical content could not be decrypted: {}", ex.getMessage());
        return ResponseEntity.internalServerError().body(ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "CLINICAL_CONTENT_UNREADABLE",
                "El contenido clínico no se puede leer; el incidente quedó registrado para el equipo de seguridad"));
    }

    @ExceptionHandler(OpenBaoUnavailableException.class)
    ResponseEntity<ProblemDetail> keysUnavailable(OpenBaoUnavailableException ex) {
        log.error("Clinical keys are unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE,
                "CLINICAL_KEYS_UNAVAILABLE", "Las claves clínicas no están disponibles en este momento; intente de nuevo en unos minutos"));
    }
}
