package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.AuthorizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

final class AuthorizationRequests {

    record Grant(@NotBlank String number, @NotNull AuthorizationType type, String authorizedBy,
                 @PositiveOrZero BigDecimal copayment, LocalDate validFrom, LocalDate validTo,
                 Set<UUID> authorizedItems) {
    }

    record Revocation(@NotBlank String reason) {
    }

    private AuthorizationRequests() {
    }
}
