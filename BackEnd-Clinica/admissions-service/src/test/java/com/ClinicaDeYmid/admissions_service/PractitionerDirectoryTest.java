package com.ClinicaDeYmid.admissions_service;

import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerDirectory;
import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerDirectoryException;
import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerLookup;
import com.ClinicaDeYmid.admissions_service.application.practitioner.PractitionerRegistry;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReferences;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PractitionerDirectoryTest {

    private final Map<UUID, PractitionerReference> local = new HashMap<>();
    private final AtomicInteger registryCalls = new AtomicInteger();

    @Test
    void readsFromTheLocalCopyWithoutCallingTheRegistry() {
        UUID uuid = UUID.randomUUID();
        local.put(uuid, reference(uuid, 1, "ACTIVE"));

        PractitionerReference found = directory(new PractitionerLookup.NotFound()).require(uuid);

        assertThat(found.fullName()).isEqualTo("Laura Restrepo");
        assertThat(registryCalls).hasValue(0);
    }

    @Test
    void fallsBackToTheRegistryAndKeepsWhatItFinds() {
        UUID uuid = UUID.randomUUID();

        PractitionerReference found = directory(
                new PractitionerLookup.Found(reference(uuid, 3, "ACTIVE"))).require(uuid);

        assertThat(found.registrationNumber()).isEqualTo("RM-12345");
        assertThat(registryCalls).hasValue(1);
        assertThat(local).containsKey(uuid);
    }

    @Test
    void anUnknownPractitionerIsNotFound() {
        assertThatThrownBy(() -> directory(new PractitionerLookup.NotFound()).require(UUID.randomUUID()))
                .isInstanceOf(PractitionerDirectoryException.PractitionerNotFound.class);
    }

    @Test
    void aDirectoryThatDoesNotAnswerIsNotTheSameAsAnUnknownPractitioner() {
        assertThatThrownBy(() -> directory(new PractitionerLookup.Unavailable()).require(UUID.randomUUID()))
                .isInstanceOf(PractitionerDirectoryException.DirectoryUnavailable.class);
    }

    @Test
    void anInactivePractitionerCannotTakeAnEpisode() {
        UUID uuid = UUID.randomUUID();
        local.put(uuid, reference(uuid, 1, "INACTIVE"));

        assertThatThrownBy(() -> directory(new PractitionerLookup.NotFound()).require(uuid))
                .isInstanceOf(PractitionerDirectoryException.PractitionerNotAvailable.class)
                .hasMessageContaining("Laura Restrepo");
    }

    @Test
    void anOlderEventNeverOverwritesANewerCopy() {
        UUID uuid = UUID.randomUUID();
        PractitionerDirectory directory = directory(new PractitionerLookup.NotFound());

        directory.apply(reference(uuid, 5, "ACTIVE"));
        directory.apply(new PractitionerReference(uuid, 2, "Nombre viejo", "RM-0", null, "ACTIVE", null));

        assertThat(local.get(uuid).sourceVersion()).isEqualTo(5);
        assertThat(local.get(uuid).fullName()).isEqualTo("Laura Restrepo");
    }

    private PractitionerDirectory directory(PractitionerLookup lookup) {
        PractitionerReferences references = new PractitionerReferences() {
            @Override
            public Optional<PractitionerReference> find(UUID practitionerUuid) {
                return Optional.ofNullable(local.get(practitionerUuid));
            }

            @Override
            public boolean saveIfNewer(PractitionerReference reference) {
                PractitionerReference current = local.get(reference.practitionerUuid());
                if (current != null && current.sourceVersion() >= reference.sourceVersion()) {
                    return false;
                }
                local.put(reference.practitionerUuid(), reference);
                return true;
            }
        };
        PractitionerRegistry registry = practitionerUuid -> {
            registryCalls.incrementAndGet();
            return lookup;
        };
        return new PractitionerDirectory(references, registry);
    }

    private static PractitionerReference reference(UUID uuid, long version, String status) {
        return new PractitionerReference(uuid, version, "Laura Restrepo", "RM-12345", "Medicina interna", status, null);
    }
}
