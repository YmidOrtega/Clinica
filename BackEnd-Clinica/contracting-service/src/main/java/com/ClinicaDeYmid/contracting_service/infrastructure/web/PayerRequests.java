package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.domain.ContactInfo;
import com.ClinicaDeYmid.contracting_service.domain.Nit;
import com.ClinicaDeYmid.contracting_service.domain.PayerIdentity;
import com.ClinicaDeYmid.contracting_service.domain.PayerRegistration;
import com.ClinicaDeYmid.contracting_service.domain.PayerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

final class PayerRequests {

    private static final String REQUIRED = "es obligatorio";

    private PayerRequests() {
    }

    record Register(
            @NotNull(message = REQUIRED) @Valid Identity identity,
            @NotNull(message = REQUIRED) @Valid Contact contact) {

        PayerRegistration toDomain() {
            return new PayerRegistration(identity.socialReason(), new Nit(identity.nit()), identity.type(),
                    identity.adresCode(), contact.toDomain());
        }
    }

    record Identity(String socialReason, String nit, PayerType type, String adresCode) {
        PayerIdentity toDomain() {
            return new PayerIdentity(socialReason, new Nit(nit), type, adresCode);
        }
    }

    record Contact(String address, String phone, String billingEmail) {
        ContactInfo toDomain() {
            return new ContactInfo(address, phone, billingEmail);
        }
    }

    record Search(String nit, String socialReason) {
    }

    record StatusChange(String reason) {
    }
}
