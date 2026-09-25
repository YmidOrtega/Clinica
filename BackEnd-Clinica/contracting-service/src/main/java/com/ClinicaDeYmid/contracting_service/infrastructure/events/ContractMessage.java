package com.ClinicaDeYmid.contracting_service.infrastructure.events;

import com.ClinicaDeYmid.contracting_service.domain.AuthorizationRequirement;
import com.ClinicaDeYmid.contracting_service.domain.Contract;
import com.ClinicaDeYmid.contracting_service.domain.ContractPackage;
import com.ClinicaDeYmid.contracting_service.domain.ContractTariffException;
import com.ClinicaDeYmid.contracting_service.domain.FundingAgreement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record ContractMessage(UUID eventId, String type, Instant occurredAt, String traceId, UUID contractUuid,
                       PayerView payer, String number, String name, String modality, LocalDate validFrom,
                       LocalDate validTo, String status, long version, TariffTermsView tariffTerms,
                       List<ExceptionView> tariffExceptions, List<PackageView> packages, FundingView funding,
                       List<RequirementView> authorizationRequirements) {

    static final String AGGREGATE_TYPE = "contracting.contracts";

    static ContractMessage of(Contract contract, String type, List<ContractTariffException> exceptions,
                              List<ContractPackage> packages, FundingAgreement funding,
                              List<AuthorizationRequirement> requirements, UUID eventId, Instant occurredAt,
                              String traceId) {
        return new ContractMessage(eventId, type, occurredAt, traceId, contract.uuid(),
                new PayerView(contract.payer().uuid(), contract.payer().nit().formatted(), contract.payer().socialReason()),
                contract.number(), contract.name(), contract.modality().name(), contract.validFrom(), contract.validTo(),
                contract.status().code().name(), contract.version(), TariffTermsView.from(contract),
                exceptions.stream().filter(exception -> exception.revokedFrom() == null).map(ExceptionView::from).toList(),
                packages.stream().filter(agreed -> agreed.revokedFrom() == null).map(PackageView::from).toList(),
                FundingView.from(funding),
                requirements.stream().filter(requirement -> requirement.revokedFrom() == null)
                        .map(RequirementView::from).toList());
    }

    record RequirementView(UUID uuid, String cupsCode, LocalDate validFrom) {

        static RequirementView from(AuthorizationRequirement requirement) {
            return new RequirementView(requirement.uuid(), requirement.cupsCode(), requirement.validFrom());
        }
    }

    record PayerView(UUID uuid, String nit, String socialReason) {
    }

    record TariffTermsView(UUID manualVersionUuid, String manualCode, String versionLabel, String unit,
                           BigDecimal unitValue, BigDecimal factor) {

        static TariffTermsView from(Contract contract) {
            if (contract.tariffVersion() == null) {
                return null;
            }
            return new TariffTermsView(contract.tariffVersion().uuid(), contract.tariffVersion().manual().code(),
                    contract.tariffVersion().label(), contract.tariffVersion().manual().unit().name(),
                    contract.tariffVersion().unitValue(), contract.tariffFactor());
        }
    }

    record ExceptionView(UUID uuid, String cupsCode, BigDecimal agreedPrice, LocalDate validFrom) {

        static ExceptionView from(ContractTariffException exception) {
            return new ExceptionView(exception.uuid(), exception.cupsCode(), exception.agreedPrice(), exception.validFrom());
        }
    }

    record PackageView(UUID uuid, String code, String name, BigDecimal price, Set<String> includedCodes,
                       LocalDate validFrom) {

        static PackageView from(ContractPackage agreed) {
            return new PackageView(agreed.uuid(), agreed.code(), agreed.name(), agreed.price(), agreed.includedCodes(),
                    agreed.validFrom());
        }
    }

    record FundingView(UUID uuid, BigDecimal perCapitaValue, BigDecimal budgetCeiling, String periodicity,
                       LocalDate validFrom) {

        static FundingView from(FundingAgreement agreement) {
            if (agreement == null) {
                return null;
            }
            return new FundingView(agreement.uuid(), agreement.perCapitaValue(), agreement.budgetCeiling(),
                    agreement.periodicity().name(), agreement.validFrom());
        }
    }
}
