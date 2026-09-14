package com.ClinicaDeYmid.clinical_history_service.domain.encounter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Encounters {

    void add(Encounter encounter);

    Optional<Encounter> find(UUID id);

    Optional<Encounter> lock(UUID id);

    void close(EncounterClosure closure);

    List<Encounter> ofSubjects(Collection<UUID> patientUuids, int page, int size);
}
