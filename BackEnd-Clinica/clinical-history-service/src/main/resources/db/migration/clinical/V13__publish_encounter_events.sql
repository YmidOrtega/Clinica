ALTER TABLE clinical_outbox.outbox_events
    DROP CHECK chk_outbox_events_aggregatetype,
    DROP CHECK chk_outbox_events_type,
    ADD CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype IN ('clinical.access-audit', 'clinical.encounters')),
    ADD CONSTRAINT chk_outbox_events_type CHECK (
        (aggregatetype = 'clinical.access-audit' AND type IN ('ClinicalRecordAccessed', 'ClinicalRecordAccessDenied'))
        OR (aggregatetype = 'clinical.encounters'
            AND type IN ('EncounterOpened', 'ClinicalNoteSigned', 'ClinicalNoteVoided', 'EncounterClosed')));
