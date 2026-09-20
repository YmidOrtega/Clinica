package com.ClinicaDeYmid.admissions_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class AttendingPractitioner {

    @Column(name = "attending_practitioner_uuid")
    private UUID practitionerUuid;

    @Column(name = "attending_practitioner_name", length = 200)
    private String fullName;

    @Column(name = "attending_registration_number", length = 60)
    private String registrationNumber;

    protected AttendingPractitioner() {
    }

    public static AttendingPractitioner of(UUID practitionerUuid, String fullName, String registrationNumber) {
        AttendingPractitioner attending = new AttendingPractitioner();
        attending.practitionerUuid = DomainRules.required(practitionerUuid, "practitionerUuid");
        attending.fullName = DomainRules.requiredText(fullName, "fullName", 200);
        attending.registrationNumber = DomainRules.requiredText(registrationNumber, "registrationNumber", 60);
        return attending;
    }

    public UUID practitionerUuid() {
        return practitionerUuid;
    }

    public String fullName() {
        return fullName;
    }

    public String registrationNumber() {
        return registrationNumber;
    }
}
