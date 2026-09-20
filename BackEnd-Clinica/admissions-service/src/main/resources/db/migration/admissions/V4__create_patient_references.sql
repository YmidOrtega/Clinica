CREATE TABLE admissions.patient_references (
    uuid                    UUID         NOT NULL,
    kind                    VARCHAR(20)  NOT NULL,
    source_version          BIGINT       NOT NULL,
    document_type           VARCHAR(40),
    document_number         VARCHAR(20),
    first_names             VARCHAR(100),
    last_names              VARCHAR(100),
    birth_date              DATE,
    code                    VARCHAR(14),
    estimated_birth_year    INTEGER,
    sex                     VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    date_of_death           DATE,
    health_regime           VARCHAR(20),
    payer_uuid              UUID,
    identified_patient_uuid UUID,
    updated_at              TIMESTAMP(6) NOT NULL,

    CONSTRAINT pk_patient_references PRIMARY KEY (uuid),
    CONSTRAINT chk_patient_references_version CHECK (source_version >= 0),
    CONSTRAINT chk_patient_references_sex CHECK (sex IN ('FEMALE', 'MALE', 'INDETERMINATE')),
    CONSTRAINT chk_patient_references_kind CHECK (
        (kind = 'REGISTERED'
            AND document_type IS NOT NULL AND document_number IS NOT NULL
            AND first_names IS NOT NULL AND last_names IS NOT NULL
            AND birth_date IS NOT NULL AND health_regime IS NOT NULL
            AND code IS NULL AND estimated_birth_year IS NULL AND identified_patient_uuid IS NULL
            AND status IN ('ACTIVE', 'INACTIVE', 'DECEASED'))
        OR (kind = 'UNIDENTIFIED'
            AND code IS NOT NULL AND estimated_birth_year IS NOT NULL
            AND document_type IS NULL AND document_number IS NULL
            AND first_names IS NULL AND last_names IS NULL AND birth_date IS NULL
            AND health_regime IS NULL AND payer_uuid IS NULL
            AND status IN ('UNIDENTIFIED', 'IDENTIFIED', 'DECEASED'))),
    CONSTRAINT chk_patient_references_identification
        CHECK ((status = 'IDENTIFIED') = (identified_patient_uuid IS NOT NULL) OR kind = 'REGISTERED')
);

CREATE INDEX idx_patient_references_document
    ON admissions.patient_references (document_type, document_number)
    WHERE kind = 'REGISTERED';
