package com.ClinicaDeYmid.billing_service.application.clinical;

import java.util.UUID;

public interface ClinicalFacts {

    void record(ClinicalFact fact);

    CareRecord ofAdmission(UUID admissionUuid);
}
