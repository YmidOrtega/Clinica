package com.ClinicaDeYmid.clinical_history_service.application.patient;

import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReference;
import com.ClinicaDeYmid.clinical_history_service.domain.patient.PatientReferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PatientReferenceProjection {

    private static final Logger log = LoggerFactory.getLogger(PatientReferenceProjection.class);

    private final PatientReferences references;

    public PatientReferenceProjection(PatientReferences references) {
        this.references = references;
    }

    public void apply(PatientReference reference) {
        if (references.saveIfNewer(reference)) {
            log.debug("Patient reference {} updated to version {}", reference.uuid(), reference.version());
        } else {
            log.debug("Ignored stale or repeated patient event {} version {}", reference.uuid(), reference.version());
        }
    }
}
