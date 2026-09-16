package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractModality;
import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import com.ClinicaDeYmid.contracting_service.domain.ContractStatus;
import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

final class ContractResponses {

    private ContractResponses() {
    }

    record ContractView(UUID uuid, long version, UUID payerUuid, String payerNit, String payerName, String number,
                        String name, ContractModality modality, String modalityLabel, LocalDate validFrom,
                        LocalDate validTo, TariffTermsView tariffTerms, StatusView status, Instant createdAt,
                        Instant updatedAt) {

        static ContractView from(Contract contract) {
            return new ContractView(contract.uuid(), contract.version(), contract.payer().uuid(),
                    contract.payer().nit().formatted(), contract.payer().socialReason(), contract.number(),
                    contract.name(), contract.modality(), contract.modality().label(), contract.validFrom(),
                    contract.validTo(), TariffTermsView.from(contract), StatusView.from(contract.status()),
                    contract.createdAt(), contract.updatedAt());
        }
    }

    record TariffTermsView(UUID manualVersionUuid, String manualCode, String manualLabel, BigDecimal factor) {

        static TariffTermsView from(Contract contract) {
            if (contract.tariffVersion() == null) {
                return null;
            }
            return new TariffTermsView(contract.tariffVersion().uuid(), contract.tariffVersion().manual().code(),
                    contract.tariffVersion().label(), contract.tariffFactor());
        }
    }

    record StatusView(ContractStatus.Code code, String reason, Instant since, boolean billable) {

        static StatusView from(ContractStatus status) {
            return switch (status) {
                case ContractStatus.Draft draft -> new StatusView(status.code(), null, null, false);
                case ContractStatus.Active active -> new StatusView(status.code(), null, active.since(), true);
                case ContractStatus.Suspended suspended ->
                        new StatusView(status.code(), suspended.reason(), suspended.since(), false);
                case ContractStatus.Terminated terminated ->
                        new StatusView(status.code(), terminated.reason(), terminated.since(), false);
            };
        }
    }

    record ExceptionView(UUID uuid, UUID contractUuid, String cupsCode, BigDecimal agreedPrice, String reason,
                         LocalDate validFrom, LocalDate revokedFrom, String revocationReason, Instant registeredAt,
                         String registeredBy) {

        static ExceptionView from(ContractTariffException exception) {
            return new ExceptionView(exception.uuid(), exception.contract().uuid(), exception.cupsCode(),
                    exception.agreedPrice(), exception.reason(), exception.validFrom(), exception.revokedFrom(),
                    exception.revocationReason(), exception.registeredAt(), exception.registeredBy());
        }
    }

    record PackageView(UUID uuid, UUID contractUuid, String code, String name, BigDecimal price,
                       Set<String> includedCodes, LocalDate validFrom, LocalDate revokedFrom) {

        static PackageView from(ContractPackage agreed) {
            return new PackageView(agreed.uuid(), agreed.contract().uuid(), agreed.code(), agreed.name(),
                    agreed.price(), agreed.includedCodes(), agreed.validFrom(), agreed.revokedFrom());
        }
    }
}
