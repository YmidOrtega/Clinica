package com.ClinicaDeYmid.admissions_service.application.practitioner;

import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.admissions_service.domain.practitioner.PractitionerReferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PractitionerDirectory {

    private static final Logger log = LoggerFactory.getLogger(PractitionerDirectory.class);

    private final PractitionerReferences references;
    private final PractitionerRegistry registry;

    public PractitionerDirectory(PractitionerReferences references, PractitionerRegistry registry) {
        this.references = references;
        this.registry = registry;
    }

    public void apply(PractitionerReference reference) {
        if (references.saveIfNewer(reference)) {
            log.debug("Practitioner {} updated to version {}", reference.practitionerUuid(),
                    reference.sourceVersion());
        }
    }

    public PractitionerReference require(UUID practitionerUuid) {
        PractitionerReference reference = references.find(practitionerUuid)
                .orElseGet(() -> fromRegistry(practitionerUuid));
        if (!reference.attends()) {
            throw new PractitionerDirectoryException.PractitionerNotAvailable(reference.fullName());
        }
        return reference;
    }

    private PractitionerReference fromRegistry(UUID practitionerUuid) {
        log.debug("Practitioner {} is not in the local copy; asking practitioners-service", practitionerUuid);
        PractitionerLookup lookup = registry.fetch(practitionerUuid);
        return switch (lookup) {
            case PractitionerLookup.Found found -> {
                apply(found.reference());
                yield found.reference();
            }
            case PractitionerLookup.NotFound ignored -> throw new PractitionerDirectoryException.PractitionerNotFound();
            case PractitionerLookup.Unavailable ignored ->
                    throw new PractitionerDirectoryException.DirectoryUnavailable();
        };
    }
}
