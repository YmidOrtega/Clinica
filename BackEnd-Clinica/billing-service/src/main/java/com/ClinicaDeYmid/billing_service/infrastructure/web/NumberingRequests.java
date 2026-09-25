package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.ResolutionTerms;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

final class NumberingRequests {

    record Registration(@NotBlank String resolutionNumber,
                        @NotNull LocalDate issuedOn,
                        String prefix,
                        @NotNull Long rangeFrom,
                        @NotNull Long rangeTo,
                        @NotNull LocalDate validFrom,
                        @NotNull LocalDate validUntil,
                        @NotBlank String technicalKey) {

        ResolutionTerms toDomain() {
            return new ResolutionTerms(resolutionNumber, issuedOn, prefix, rangeFrom, rangeTo, validFrom, validUntil,
                    technicalKey);
        }
    }

    record Reason(@NotBlank String reason) {
    }

    private NumberingRequests() {
    }
}
