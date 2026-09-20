ALTER TABLE admissions.admissions
    ADD COLUMN coverage_status          VARCHAR(20),
    ADD COLUMN coverage_contract_uuid   UUID,
    ADD COLUMN coverage_contract_number VARCHAR(60),
    ADD COLUMN coverage_payer_uuid      UUID,
    ADD COLUMN coverage_detail          VARCHAR(300),
    ADD COLUMN coverage_checked_at      TIMESTAMP(6);

ALTER TABLE admissions.admissions
    ADD CONSTRAINT chk_admissions_coverage_status
        CHECK (coverage_status IS NULL OR coverage_status IN ('COVERED', 'NOT_COVERED', 'UNKNOWN')),
    ADD CONSTRAINT chk_admissions_coverage_contract
        CHECK ((coverage_status = 'COVERED'
                    AND coverage_contract_uuid IS NOT NULL AND coverage_contract_number IS NOT NULL)
            OR (coverage_status <> 'COVERED' AND coverage_contract_uuid IS NULL)),
    ADD CONSTRAINT chk_admissions_coverage_detail
        CHECK ((coverage_status IN ('NOT_COVERED', 'UNKNOWN') AND coverage_detail IS NOT NULL)
            OR coverage_status IS NULL OR coverage_status = 'COVERED'),
    ADD CONSTRAINT chk_admissions_coverage_moment
        CHECK ((coverage_status IS NULL) = (coverage_checked_at IS NULL));

CREATE INDEX idx_admissions_coverage_pending
    ON admissions.admissions (coverage_checked_at)
    WHERE coverage_status IN ('NOT_COVERED', 'UNKNOWN');

ALTER TABLE admissions_history.admissions_aud
    ADD COLUMN coverage_status          VARCHAR(20),
    ADD COLUMN coverage_contract_uuid   UUID,
    ADD COLUMN coverage_contract_number VARCHAR(60),
    ADD COLUMN coverage_payer_uuid      UUID,
    ADD COLUMN coverage_detail          VARCHAR(300),
    ADD COLUMN coverage_checked_at      TIMESTAMP(6);
