package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.PortfolioCommands;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCategory;
import com.ClinicaDeYmid.contracting_service.domain.ServiceCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

final class PortfolioRequests {

    private PortfolioRequests() {
    }

    record Item(String cupsCode, String clinicCode, String name, ServiceCategory category) {

        ServiceCode code() {
            return new ServiceCode(cupsCode, clinicCode);
        }

        PortfolioCommands.Entry toEntry() {
            return new PortfolioCommands.Entry(code(), name, category);
        }
    }

    record Import(@NotEmpty(message = "debe traer al menos un servicio")
                  @Size(max = 5000, message = "no puede superar 5000 servicios por carga")
                  @Valid List<Item> items) {

        List<PortfolioCommands.Entry> toEntries() {
            return items.stream().map(Item::toEntry).toList();
        }
    }

    record Search(String cupsCode, String clinicCode, String name) {
    }

    record StatusChange(String reason) {
    }
}
