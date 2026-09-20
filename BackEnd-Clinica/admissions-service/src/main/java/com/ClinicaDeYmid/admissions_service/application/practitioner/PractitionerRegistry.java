package com.ClinicaDeYmid.admissions_service.application.practitioner;

import java.util.UUID;

public interface PractitionerRegistry {

    PractitionerLookup fetch(UUID practitionerUuid);
}
