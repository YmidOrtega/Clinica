CREATE TABLE admissions.practitioner_references (
    practitioner_uuid   UUID         NOT NULL,
    source_version      BIGINT       NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    registration_number VARCHAR(60)  NOT NULL,
    specialty           VARCHAR(200),
    status              VARCHAR(20)  NOT NULL,
    user_uuid           UUID,
    updated_at          TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_practitioner_references PRIMARY KEY (practitioner_uuid),
    CONSTRAINT chk_practitioner_references_version CHECK (source_version >= 0)
);

ALTER TABLE admissions.admissions
    ADD COLUMN attending_practitioner_uuid UUID,
    ADD COLUMN attending_practitioner_name VARCHAR(200),
    ADD COLUMN attending_registration_number VARCHAR(60);

ALTER TABLE admissions.admissions
    ADD CONSTRAINT chk_admissions_attending
        CHECK ((attending_practitioner_uuid IS NULL AND attending_practitioner_name IS NULL
                    AND attending_registration_number IS NULL)
            OR (attending_practitioner_uuid IS NOT NULL AND attending_practitioner_name IS NOT NULL
                    AND attending_registration_number IS NOT NULL));

ALTER TABLE admissions_history.admissions_aud
    ADD COLUMN attending_practitioner_uuid UUID,
    ADD COLUMN attending_practitioner_name VARCHAR(200),
    ADD COLUMN attending_registration_number VARCHAR(60);
