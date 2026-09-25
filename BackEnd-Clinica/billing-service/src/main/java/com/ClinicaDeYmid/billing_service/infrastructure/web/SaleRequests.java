package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.application.sale.LineRequest;
import com.ClinicaDeYmid.billing_service.application.sale.SaleCommands;
import com.ClinicaDeYmid.billing_service.domain.SaleType;
import com.ClinicaDeYmid.billing_service.domain.SurgicalRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class SaleRequests {

    record Opening(@NotBlank @Pattern(regexp = "^ADM-[0-9]{4}-[0-9]{6}$") String admissionNumber,
                   @NotNull SaleType.Code type,
                   Boolean preloadAuthorized,
                   LocalDate performedOn) {

        boolean preload() {
            return preloadAuthorized == null || preloadAuthorized;
        }

        SaleType toType() {
            return switch (type) {
                case NON_SURGICAL -> new SaleType.NonSurgical();
                case SURGICAL -> new SaleType.Surgical(performedOn);
            };
        }
    }

    record Procedure(UUID portfolioItemUuid, String cupsCode, @Size(max = 30) String route) {
    }

    record Member(@NotNull SurgicalRole role, @NotNull UUID practitionerUuid) {
    }

    record Team(@NotNull @Size(min = 1, max = 3) List<@Valid Member> members) {

        List<SaleCommands.TeamAssignment> toAssignments() {
            return members.stream().map(member -> new SaleCommands.TeamAssignment(member.role(),
                    member.practitionerUuid())).toList();
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

    record ManualPrice(@NotNull @Positive BigDecimal unitPrice, @NotBlank String reason) {
    }

    private SaleRequests() {
    }
}
