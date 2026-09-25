package com.ClinicaDeYmid.billing_service.infrastructure.web;

import com.ClinicaDeYmid.billing_service.domain.IssuerProfile;
import com.ClinicaDeYmid.billing_service.domain.PersonType;
import com.ClinicaDeYmid.billing_service.domain.TaxResponsibility;
import com.ClinicaDeYmid.billing_service.domain.TaxScheme;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

final class IssuerRequests {

    record Profile(@NotNull PersonType personType,
                   @NotBlank String legalName,
                   String tradeName,
                   @NotNull TaxScheme taxScheme,
                   @NotEmpty Set<TaxResponsibility> taxResponsibilities,
                   @NotBlank String addressLine,
                   @NotBlank String municipalityCode,
                   @NotBlank String cityName,
                   @NotBlank String departmentName,
                   String postalCode,
                   @NotBlank String email,
                   @NotBlank String phone,
                   @NotBlank String healthProviderCode) {

        IssuerProfile toDomain() {
            return new IssuerProfile(personType, legalName, tradeName, taxScheme, taxResponsibilities, addressLine,
                    municipalityCode, cityName, departmentName, postalCode, email, phone, healthProviderCode);
        }
    }

    record Configuration(@NotBlank String nit, @NotNull Integer verificationDigit, @NotNull @Valid Profile profile) {
    }

    private IssuerRequests() {
    }
}
