package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.TariffCommands;
import com.ClinicaDeYmid.contracting_service.domain.PriceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class TariffRequests {

    private TariffRequests() {
    }

    record Manual(String code, String name, PriceUnit unit) {
    }

    record Version(String label, BigDecimal unitValue, LocalDate validFrom) {
    }

    record Item(String cupsCode, String description, BigDecimal value) {

        TariffCommands.Entry toEntry() {
            return new TariffCommands.Entry(cupsCode, description, value);
        }
    }

    record Load(@NotEmpty(message = "debe traer al menos una tarifa")
                @Size(max = 20000, message = "no puede superar 20000 tarifas por carga")
                @Valid List<Item> items) {

        List<TariffCommands.Entry> toEntries() {
            return items.stream().map(Item::toEntry).toList();
        }
    }
}
