ALTER TABLE admissions.admissions
    ADD COLUMN bed_uuid UUID;

ALTER TABLE admissions.admissions
    ADD CONSTRAINT chk_admissions_closed_without_bed
        CHECK (status IN ('REGISTERED', 'ACTIVE') OR bed_uuid IS NULL);

CREATE UNIQUE INDEX uq_admissions_open_bed
    ON admissions.admissions (bed_uuid)
    WHERE bed_uuid IS NOT NULL;

ALTER TABLE admissions_history.admissions_aud
    ADD COLUMN bed_uuid UUID;
