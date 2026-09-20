package com.ClinicaDeYmid.clinical_history_service.domain.practitioner;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PractitionerReferences {

    Optional<PractitionerReference> find(UUID userUuid);

    Map<UUID, PractitionerReference> findAll(Collection<UUID> userUuids);

    boolean saveIfNewer(PractitionerReference reference);

    void accountUnlinkedFrom(UUID practitionerUuid);
}
