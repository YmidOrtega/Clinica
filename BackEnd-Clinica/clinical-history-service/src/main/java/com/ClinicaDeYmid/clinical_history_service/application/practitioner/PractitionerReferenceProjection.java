package com.ClinicaDeYmid.clinical_history_service.application.practitioner;

import com.ClinicaDeYmid.clinical_history_service.domain.practitioner.PractitionerReference;
import com.ClinicaDeYmid.clinical_history_service.domain.practitioner.PractitionerReferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PractitionerReferenceProjection {

    private static final Logger log = LoggerFactory.getLogger(PractitionerReferenceProjection.class);

    private final PractitionerReferences references;

    public PractitionerReferenceProjection(PractitionerReferences references) {
        this.references = references;
    }

    public void apply(PractitionerReference reference) {
        if (references.saveIfNewer(reference)) {
            log.debug("Practitioner reference {} updated to version {}", reference.userUuid(), reference.sourceVersion());
        } else {
            log.debug("Ignored stale practitioner event {} version {}", reference.userUuid(), reference.sourceVersion());
        }
    }

    public void accountUnlinkedFrom(UUID practitionerUuid) {
        references.accountUnlinkedFrom(practitionerUuid);
        log.debug("Practitioner {} no longer has an account linked", practitionerUuid);
    }
}
