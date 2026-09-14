package com.ClinicaDeYmid.clinical_history_service.application.patient;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PatientDirectory {

    private static final Logger log = LoggerFactory.getLogger(PatientDirectory.class);

    private final PatientReferences references;
    private final PatientRegistry registry;

    public PatientDirectory(PatientReferences references, PatientRegistry registry) {
        this.references = references;
        this.registry = registry;
    }

    public PatientLookup find(UUID uuid) {
        return references.find(uuid)
                .<PatientLookup>map(PatientLookup.Found::new)
                .orElseGet(() -> fetchFromRegistry(uuid));
    }

    public List<UUID> subjectsOf(UUID patientUuid) {
        return references.subjectsOf(patientUuid);
    }

    private PatientLookup fetchFromRegistry(UUID uuid) {
        PatientLookup lookup = registry.fetch(uuid);
        if (lookup instanceof PatientLookup.Found found) {
            references.saveIfNewer(found.patient());
            log.info("Patient {} was not in the local copy yet; loaded it from patient-service", uuid);
        }
        return lookup;
    }
}
