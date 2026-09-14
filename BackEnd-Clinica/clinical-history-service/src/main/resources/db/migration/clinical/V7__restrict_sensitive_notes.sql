ALTER TABLE clinical_ledger.notes
    ADD COLUMN restriction VARCHAR(20) NULL AFTER type,
    ADD CONSTRAINT chk_notes_restriction CHECK (restriction IN ('MENTAL_HEALTH', 'SEXUAL_HEALTH', 'HIV', 'VIOLENCE'));

ALTER TABLE clinical_workspace.note_drafts
    ADD COLUMN restriction VARCHAR(20) NULL AFTER type,
    ADD CONSTRAINT chk_note_drafts_restriction CHECK (restriction IN ('MENTAL_HEALTH', 'SEXUAL_HEALTH', 'HIV', 'VIOLENCE'));
