package com.ClinicaDeYmid.admissions_service.domain.practitioner;

import java.util.Optional;
import java.util.UUID;

public interface PractitionerReferences {

    Optional<PractitionerReference> find(UUID practitionerUuid);

    boolean saveIfNewer(PractitionerReference reference);
}
