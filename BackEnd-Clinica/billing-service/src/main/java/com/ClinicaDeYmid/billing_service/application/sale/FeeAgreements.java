package com.ClinicaDeYmid.billing_service.application.sale;

import com.ClinicaDeYmid.billing_service.domain.FeeAgreementTerms;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FeeAgreements {

    Optional<FeeAgreementTerms> inForce(UUID practitionerUuid, LocalDate on);
}
