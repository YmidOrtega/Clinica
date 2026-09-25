package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.TariffCommands;
import com.ClinicaDeYmid.contracting_service.domain.PriceUnit;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalBasis;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalComponent;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalComponentRule;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalRange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    record Item(String cupsCode, String description, BigDecimal value, BigDecimal surgicalBasis) {

        TariffCommands.Entry toEntry() {
            return new TariffCommands.Entry(cupsCode, description, value, surgicalBasis);
        }
    }

    record Range(BigDecimal from, BigDecimal to, BigDecimal value) {

        SurgicalRange toRange() {
            return new SurgicalRange(from, to, value);
        }
    }

    record ComponentRule(SurgicalComponent component, SurgicalComponentRule.Mode mode, BigDecimal rate,
                         List<Range> ranges, BigDecimal minimumBasis, BigDecimal sameRoutePercent,
                         BigDecimal differentRoutePercent) {

        SurgicalComponentRule.Definition toDefinition() {
            return new SurgicalComponentRule.Definition(component, mode, rate,
                    ranges == null ? null : ranges.stream().map(Range::toRange).toList(), minimumBasis,
                    sameRoutePercent, differentRoutePercent);
        }
    }

    record SurgicalRules(@NotNull(message = "es obligatorio") SurgicalBasis basis,
                         @NotEmpty(message = "debe traer al menos la regla del cirujano")
                         @Size(max = 5, message = "no puede traer más de cinco componentes")
                         List<ComponentRule> components) {

        List<SurgicalComponentRule.Definition> toDefinitions() {
            return components.stream().map(ComponentRule::toDefinition).toList();
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
