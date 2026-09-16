ALTER TABLE patients
    DROP CHECK chk_patients_health_provider_nit;

ALTER TABLE patients
    DROP CHECK chk_patients_affiliation_consistency;

DROP INDEX idx_patients_health_provider ON patients;

ALTER TABLE patients
    CHANGE COLUMN health_provider_nit payer_uuid CHAR(36) NULL;

UPDATE patients SET payer_uuid = NULL WHERE payer_uuid IS NOT NULL;

ALTER TABLE patients
    ADD CONSTRAINT chk_patients_payer_uuid
        CHECK (payer_uuid IS NULL
            OR REGEXP_LIKE(payer_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c'));

ALTER TABLE patients
    ADD CONSTRAINT chk_patients_affiliation_consistency
        CHECK ((health_regime = 'UNINSURED' AND affiliate_type IS NULL AND payer_uuid IS NULL AND policy_number IS NULL)
            OR (health_regime <> 'UNINSURED' AND affiliate_type IS NOT NULL AND payer_uuid IS NOT NULL));

CREATE INDEX idx_patients_payer ON patients (payer_uuid);

ALTER TABLE patients_aud
    CHANGE COLUMN health_provider_nit payer_uuid CHAR(36) NULL;

UPDATE patients_aud SET payer_uuid = NULL WHERE payer_uuid IS NOT NULL;
