package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PricingQueries;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class PricingRequests {

    private static final String REQUIRED = "es obligatorio";

    private PricingRequests() {
    }

    record Service(String cupsCode, Integer quantity) {

        PricingQueries.Requested toRequested() {
            return new PricingQueries.Requested(cupsCode, quantity == null ? 1 : quantity);
        }
    }

    record Quote(@NotNull(message = REQUIRED) UUID contractUuid,
                 @NotNull(message = REQUIRED) LocalDate on,
                 @NotEmpty(message = "debe traer al menos un servicio")
                 @Size(max = 500, message = "no puede superar 500 servicios por consulta")
                 @Valid List<Service> services) {

        List<PricingQueries.Requested> toRequested() {
            return services.stream().map(Service::toRequested).toList();
        }
    }
}
