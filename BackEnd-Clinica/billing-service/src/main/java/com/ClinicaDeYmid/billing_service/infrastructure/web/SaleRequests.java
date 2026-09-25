package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.sale.LineRequest;
import com.ClinicaDeYmid.billing_service.domain.SaleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

final class SaleRequests {

    record Opening(@NotBlank @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber,
                   @NotNull SaleType.Code type,
                   Boolean preloadAuthorized) {

        boolean preload() {
            return preloadAuthorized == null || preloadAuthorized;
        }
    }

    record Line(UUID portfolioItemUuid, String cupsCode, @NotNull @Min(1) @Max(999) Integer quantity,
                LocalDate serviceDate) {

        LineRequest toRequest() {
            return new LineRequest(portfolioItemUuid, cupsCode, quantity, serviceDate);
        }
    }

    record Reason(@NotBlank String reason) {
    }

    private SaleRequests() {
    }
}
