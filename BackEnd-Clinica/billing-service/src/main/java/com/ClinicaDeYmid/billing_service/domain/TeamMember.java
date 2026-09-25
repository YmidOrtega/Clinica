package com.ClinicaDeYmid.billing_service.domain;

import java.util.UUID;

public record TeamMember(SurgicalRole role, UUID practitionerUuid, String fullName, String registrationNumber) {

    public TeamMember {
        DomainRules.required(role, "role");
        DomainRules.required(practitionerUuid, "practitionerUuid");
        fullName = DomainRules.requiredText(fullName, "fullName", 200);
        registrationNumber = DomainRules.optionalText(registrationNumber, "registrationNumber", 40);
    }
}
