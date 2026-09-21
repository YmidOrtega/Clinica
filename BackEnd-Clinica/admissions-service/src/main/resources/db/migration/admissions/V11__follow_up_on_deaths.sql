ALTER TABLE admissions.admissions
    ADD COLUMN death_notice_status VARCHAR(20),
    ADD COLUMN death_notice_detail VARCHAR(300),
    ADD COLUMN death_notice_at     TIMESTAMP(6);

ALTER TABLE admissions.admissions
    ADD CONSTRAINT chk_admissions_death_notice_status
        CHECK (death_notice_status IS NULL OR death_notice_status IN ('PENDING', 'SENT')),
    ADD CONSTRAINT chk_admissions_death_notice
        CHECK ((COALESCE(discharge_type, '') = 'DEATH'
                    AND death_notice_status IS NOT NULL AND death_notice_at IS NOT NULL)
            OR (COALESCE(discharge_type, '') <> 'DEATH'
                    AND death_notice_status IS NULL AND death_notice_detail IS NULL
                    AND death_notice_at IS NULL)),
    ADD CONSTRAINT chk_admissions_death_notice_detail
        CHECK ((death_notice_status = 'PENDING' AND death_notice_detail IS NOT NULL)
            OR (death_notice_status IS DISTINCT FROM 'PENDING' AND death_notice_detail IS NULL));

CREATE INDEX idx_admissions_pending_death_notice
    ON admissions.admissions (created_at)
    WHERE death_notice_status = 'PENDING';

ALTER TABLE admissions_history.admissions_aud
    ADD COLUMN death_notice_status VARCHAR(20),
    ADD COLUMN death_notice_detail VARCHAR(300),
    ADD COLUMN death_notice_at     TIMESTAMP(6);
