package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.commons.openbao.transit.OpenBaoUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class OpenBaoOutageAdviceTest {

    @Test
    void anOpenBaoOutageInTheStaffApiIsATemporaryUnavailability() {
        ResponseEntity<ProblemDetail> response = new OpenBaoOutageAdvice().unavailable(new OpenBaoUnavailableException("OpenBao failed for totp/code", null));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getBody().getProperties()).containsEntry("code", "AUTH_KEYS_UNAVAILABLE");
    }
}
