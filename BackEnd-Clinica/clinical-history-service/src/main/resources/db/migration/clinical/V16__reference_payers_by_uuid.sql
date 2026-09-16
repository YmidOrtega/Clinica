ALTER TABLE patient_references
    CHANGE COLUMN payer_uuid payer_uuid CHAR(36) NULL;

UPDATE patient_references SET payer_uuid = NULL WHERE payer_uuid IS NOT NULL;

ALTER TABLE patient_references
    ADD CONSTRAINT chk_patient_references_payer_uuid
        CHECK (payer_uuid IS NULL
            OR REGEXP_LIKE(payer_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'));
