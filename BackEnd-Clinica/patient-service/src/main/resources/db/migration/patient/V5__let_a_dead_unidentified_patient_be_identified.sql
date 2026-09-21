ALTER TABLE unidentified_patients
    DROP CHECK chk_unidentified_patients_status_consistency;

ALTER TABLE unidentified_patients
    ADD CONSTRAINT chk_unidentified_patients_status_consistency
        CHECK ((status = 'UNIDENTIFIED' AND identified_patient_uuid IS NULL AND date_of_death IS NULL)
            OR (status = 'IDENTIFIED' AND identified_patient_uuid IS NOT NULL AND status_reason IS NOT NULL
                AND TRIM(status_reason) <> '')
            OR (status = 'DECEASED' AND date_of_death IS NOT NULL AND identified_patient_uuid IS NULL));

ALTER TABLE patients
    DROP CHECK chk_patients_status_consistency;

ALTER TABLE patients
    ADD CONSTRAINT chk_patients_status_consistency
        CHECK ((status = 'ACTIVE' AND date_of_death IS NULL)
            OR (status = 'INACTIVE' AND status_reason IS NOT NULL AND TRIM(status_reason) <> '' AND date_of_death IS NULL)
            OR (status = 'DECEASED' AND date_of_death IS NOT NULL AND status_reason IS NULL));

ALTER TABLE patient_outbox.outbox_events
    DROP CHECK chk_outbox_events_type;

ALTER TABLE patient_outbox.outbox_events
    ADD CONSTRAINT chk_outbox_events_type CHECK (type IN ('PatientRegistered', 'PatientDocumentChanged',
                                                          'PatientDemographicsCorrected', 'PatientAffiliationUpdated',
                                                          'PatientDeactivated', 'PatientReactivated', 'PatientDied',
                                                          'PatientDeathReverted',
                                                          'UnidentifiedPatientRegistered', 'UnidentifiedPatientIdentified',
                                                          'UnidentifiedPatientIdentificationReverted',
                                                          'UnidentifiedPatientDied'));
