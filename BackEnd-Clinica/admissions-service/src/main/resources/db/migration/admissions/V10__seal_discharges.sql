ALTER TABLE admissions.admissions
    ADD COLUMN discharge_type               VARCHAR(20),
    ADD COLUMN discharge_notes              VARCHAR(500),
    ADD COLUMN discharge_signed_by          VARCHAR(200),
    ADD COLUMN discharge_signature_document VARCHAR(30),
    ADD COLUMN referral_reps_code           VARCHAR(20),
    ADD COLUMN referral_facility            VARCHAR(200),
    ADD COLUMN referral_reason              VARCHAR(500),
    ADD COLUMN escape_noticed_at            TIMESTAMP(6),
    ADD COLUMN death_occurred_at            TIMESTAMP(6),
    ADD COLUMN death_certificate_number     VARCHAR(60);

ALTER TABLE admissions.admissions
    ADD CONSTRAINT chk_admissions_discharge_type
        CHECK (discharge_type IS NULL
            OR discharge_type IN ('MEDICAL', 'VOLUNTARY', 'REFERRAL', 'ESCAPE', 'DEATH')),
    ADD CONSTRAINT chk_admissions_discharge_status
        CHECK ((status = 'DISCHARGED' AND discharge_type IS NOT NULL)
            OR (status <> 'DISCHARGED' AND discharge_type IS NULL)),
    ADD CONSTRAINT chk_admissions_discharge_notes
        CHECK (discharge_notes IS NULL OR COALESCE(discharge_type, '') = 'MEDICAL'),
    ADD CONSTRAINT chk_admissions_discharge_voluntary
        CHECK ((COALESCE(discharge_type, '') = 'VOLUNTARY'
                    AND discharge_signed_by IS NOT NULL AND discharge_signature_document IS NOT NULL)
            OR (COALESCE(discharge_type, '') <> 'VOLUNTARY'
                    AND discharge_signed_by IS NULL AND discharge_signature_document IS NULL)),
    ADD CONSTRAINT chk_admissions_discharge_referral
        CHECK ((COALESCE(discharge_type, '') = 'REFERRAL'
                    AND referral_reps_code IS NOT NULL AND referral_facility IS NOT NULL
                    AND referral_reason IS NOT NULL)
            OR (COALESCE(discharge_type, '') <> 'REFERRAL'
                    AND referral_reps_code IS NULL AND referral_facility IS NULL AND referral_reason IS NULL)),
    ADD CONSTRAINT chk_admissions_discharge_escape
        CHECK ((COALESCE(discharge_type, '') = 'ESCAPE' AND escape_noticed_at IS NOT NULL)
            OR (COALESCE(discharge_type, '') <> 'ESCAPE' AND escape_noticed_at IS NULL)),
    ADD CONSTRAINT chk_admissions_discharge_death
        CHECK ((COALESCE(discharge_type, '') = 'DEATH'
                    AND death_occurred_at IS NOT NULL AND death_certificate_number IS NOT NULL)
            OR (COALESCE(discharge_type, '') <> 'DEATH'
                    AND death_occurred_at IS NULL AND death_certificate_number IS NULL)),
    ADD CONSTRAINT chk_admissions_discharge_moments
        CHECK ((escape_noticed_at IS NULL OR escape_noticed_at <= status_changed_at)
            AND (death_occurred_at IS NULL OR death_occurred_at <= status_changed_at)),
    ADD CONSTRAINT chk_admissions_discharge_reps_code
        CHECK (referral_reps_code IS NULL OR referral_reps_code ~ '^[0-9]{10,14}$');

CREATE INDEX idx_admissions_discharge_type
    ON admissions.admissions (discharge_type)
    WHERE discharge_type IS NOT NULL;

ALTER TABLE admissions_history.admissions_aud
    ADD COLUMN discharge_type               VARCHAR(20),
    ADD COLUMN discharge_notes              VARCHAR(500),
    ADD COLUMN discharge_signed_by          VARCHAR(200),
    ADD COLUMN discharge_signature_document VARCHAR(30),
    ADD COLUMN referral_reps_code           VARCHAR(20),
    ADD COLUMN referral_facility            VARCHAR(200),
    ADD COLUMN referral_reason              VARCHAR(500),
    ADD COLUMN escape_noticed_at            TIMESTAMP(6),
    ADD COLUMN death_occurred_at            TIMESTAMP(6),
    ADD COLUMN death_certificate_number     VARCHAR(60);
