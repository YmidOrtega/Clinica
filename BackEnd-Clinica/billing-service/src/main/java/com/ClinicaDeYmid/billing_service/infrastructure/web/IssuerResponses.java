package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.IssuerProfile;
import com.ClinicaDeYmid.billing_service.domain.PersonType;
import com.ClinicaDeYmid.billing_service.domain.TaxResponsibility;
import com.ClinicaDeYmid.billing_service.domain.TaxScheme;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

final class IssuerResponses {

    record ResponsibilityView(TaxResponsibility code, String dianCode) {
    }

    record IssuerView(UUID uuid, String nit, int verificationDigit, PersonType personType, String legalName,
                      String tradeName, TaxScheme taxScheme, List<ResponsibilityView> taxResponsibilities,
                      String addressLine, String municipalityCode, String departmentCode, String cityName,
                      String departmentName, String postalCode, String email, String phone,
                      String healthProviderCode, String creditNotePrefix, DianEnvironment environment,
                      Instant productionSince) {

        static IssuerView from(Issuer issuer) {
            IssuerProfile profile = issuer.profile();
            List<ResponsibilityView> responsibilities = EnumSet.copyOf(profile.taxResponsibilities()).stream()
                    .map(responsibility -> new ResponsibilityView(responsibility, responsibility.dianCode()))
                    .toList();
            return new IssuerView(issuer.uuid(), issuer.nit().number(), issuer.nit().verificationDigit(),
                    profile.personType(), profile.legalName(), profile.tradeName(), profile.taxScheme(),
                    responsibilities, profile.addressLine(), profile.municipalityCode(), profile.departmentCode(),
                    profile.cityName(), profile.departmentName(), profile.postalCode(), profile.email(),
                    profile.phone(), profile.healthProviderCode(), issuer.creditNotePrefix(), issuer.environment(),
                    issuer.productionSince());
        }
    }

    private IssuerResponses() {
    }
}
