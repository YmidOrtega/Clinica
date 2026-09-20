package com.ClinicaDeYmid.admissions_service.application.patient;

import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PatientDirectory {

    private static final Logger log = LoggerFactory.getLogger(PatientDirectory.class);

    private final PatientReferences references;
    private final PatientRegistry registry;
    private final PatientReferenceProjection projection;

    public PatientDirectory(PatientReferences references, PatientRegistry registry,
                            PatientReferenceProjection projection) {
        this.references = references;
        this.registry = registry;
        this.projection = projection;
    }

    public PatientLookup resolve(UUID uuid) {
        return references.find(uuid)
                .<PatientLookup>map(PatientLookup.Found::new)
                .orElseGet(() -> askTheRegistry(uuid));
    }

    private PatientLookup askTheRegistry(UUID uuid) {
        log.debug("Patient {} is not in the local copy; asking patient-service", uuid);
        PatientLookup lookup = registry.fetch(uuid);
        if (lookup instanceof PatientLookup.Found found) {
            projection.apply(found.reference());
        }
        return lookup;
    }

    public PatientReference require(UUID uuid) {
        return switch (resolve(uuid)) {
            case PatientLookup.Found found -> found.reference();
            case PatientLookup.NotFound ignored -> throw new PatientDirectoryException.PatientNotFound();
            case PatientLookup.Unavailable ignored -> throw new PatientDirectoryException.RegistryUnavailable();
        };
    }
}
