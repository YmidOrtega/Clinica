package com.ClinicaDeYmid.clinical_history_service.application.patient;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientDirectoryTest {

    private final InMemoryReferences references = new InMemoryReferences();
    private final StubRegistry registry = new StubRegistry();
    private final PatientDirectory directory = new PatientDirectory(references, registry);

    @Test
    void answersFromTheLocalCopyWithoutCallingPatientService() {
        PatientReference patient = patient();
        references.saveIfNewer(patient);

        assertThat(directory.find(patient.uuid())).isEqualTo(new PatientLookup.Found(patient));
        assertThat(registry.requested).isEmpty();
    }

    @Test
    void loadsPatientsThatHaveNotArrivedByEventsYetAndKeepsThem() {
        PatientReference patient = patient();
        registry.answer = new PatientLookup.Found(patient);

        assertThat(directory.find(patient.uuid())).isEqualTo(new PatientLookup.Found(patient));
        assertThat(references.find(patient.uuid())).contains(patient);
    }

    @Test
    void reportsUnknownAndUnreachablePatientsWithoutStoringAnything() {
        UUID uuid = UUID.randomUUID();

        registry.answer = new PatientLookup.NotFound();
        assertThat(directory.find(uuid)).isEqualTo(new PatientLookup.NotFound());

        registry.answer = new PatientLookup.Unavailable();
        assertThat(directory.find(uuid)).isEqualTo(new PatientLookup.Unavailable());
        assertThat(references.find(uuid)).isEmpty();
    }

    private static PatientReference patient() {
        return new PatientReference.Registered(UUID.randomUUID(), 3, new PatientReference.Document("PASAPORTE", "AB123456"),
                "Luis", "Pérez", LocalDate.of(1985, 1, 20), PatientReference.Sex.MALE,
                PatientReference.Registered.Status.ACTIVE, null, "UNINSURED", null);
    }

    static final class StubRegistry implements PatientRegistry {
        final List<UUID> requested = new ArrayList<>();
        PatientLookup answer = new PatientLookup.NotFound();

        @Override
        public PatientLookup fetch(UUID uuid) {
            requested.add(uuid);
            return answer;
        }
    }

    static final class InMemoryReferences implements PatientReferences {
        private final Map<UUID, PatientReference> store = new HashMap<>();

        @Override
        public Optional<PatientReference> find(UUID uuid) {
            return Optional.ofNullable(store.get(uuid));
        }

        @Override
        public boolean saveIfNewer(PatientReference reference) {
            PatientReference current = store.get(reference.uuid());
            if (current != null && current.version() >= reference.version()) {
                return false;
            }
            store.put(reference.uuid(), reference);
            return true;
        }

        @Override
        public List<UUID> subjectsOf(UUID patientUuid) {
            return List.of(patientUuid);
        }
    }
}
