ALTER TABLE clinical_ledger.encounters
    DROP COLUMN admission_id;

ALTER TABLE clinical_ledger.encounters
    ADD COLUMN admission_uuid     CHAR(36)   NULL,
    ADD COLUMN admission_verified TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE clinical_ledger.encounters
    ADD CONSTRAINT chk_encounters_admission_uuid
        CHECK (admission_uuid IS NULL
            OR REGEXP_LIKE(admission_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    ADD CONSTRAINT chk_encounters_admission_verified
        CHECK (admission_uuid IS NOT NULL OR admission_verified = 0);

CREATE INDEX idx_encounters_admission ON clinical_ledger.encounters (admission_uuid);
