package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.Authorization;
import com.ClinicaDeYmid.admissions_service.domain.AuthorizationStatus;
import com.ClinicaDeYmid.admissions_service.domain.AuthorizationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

final class AuthorizationResponses {

    record AuthorizationView(UUID uuid, UUID admissionUuid, String admissionNumber, String number,
                             AuthorizationType type, String authorizedBy, BigDecimal copayment,
                             LocalDate validFrom, LocalDate validTo, Set<UUID> authorizedItems,
                             boolean coversEverything, AuthorizationStatus.Code status, String statusReason,
                             Instant statusChangedAt) {

        static AuthorizationView from(Authorization authorization) {
            AuthorizationStatus status = authorization.status();
            String reason = status instanceof AuthorizationStatus.Revoked revoked ? revoked.reason() : null;
            Instant at = status instanceof AuthorizationStatus.Revoked revoked ? revoked.at() : null;
            return new AuthorizationView(authorization.uuid(), authorization.admission().uuid(),
                    authorization.admission().number(), authorization.number(), authorization.type(),
                    authorization.authorizedBy(), authorization.copayment(), authorization.validFrom(),
                    authorization.validTo(), authorization.authorizedItems(),
                    authorization.authorizedItems().isEmpty(), status.code(), reason, at);
        }
    }

    private AuthorizationResponses() {
    }
}
