ALTER TABLE admissions.admissions
    ADD COLUMN triage_level   VARCHAR(3),
    ADD COLUMN triage_at      TIMESTAMP(6),
    ADD COLUMN triage_by_uuid UUID;

ALTER TABLE admissions.admissions
    ADD CONSTRAINT chk_admissions_triage_level
        CHECK (triage_level IS NULL OR triage_level IN ('I', 'II', 'III', 'IV', 'V')),
    ADD CONSTRAINT chk_admissions_triage
        CHECK ((triage_level IS NULL AND triage_at IS NULL AND triage_by_uuid IS NULL)
            OR (triage_level IS NOT NULL AND triage_at IS NOT NULL));

ALTER TABLE admissions_history.admissions_aud
    ADD COLUMN triage_level   VARCHAR(3),
    ADD COLUMN triage_at      TIMESTAMP(6),
    ADD COLUMN triage_by_uuid UUID;
