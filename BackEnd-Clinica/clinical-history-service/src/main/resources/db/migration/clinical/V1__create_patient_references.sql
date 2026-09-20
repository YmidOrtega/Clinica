CREATE TABLE patient_references (
    uuid                    CHAR(36)     NOT NULL,
    kind                    VARCHAR(20)  NOT NULL,
    source_version          BIGINT       NOT NULL,
    document_type           VARCHAR(40)  NULL,
    document_number         VARCHAR(20)  NULL,
    first_names             VARCHAR(100) NULL,
    last_names              VARCHAR(100) NULL,
    birth_date              DATE         NULL,
    code                    VARCHAR(14)  NULL,
    estimated_birth_year    INT          NULL,
    sex                     VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    date_of_death           DATE         NULL,
    health_regime           VARCHAR(20)  NULL,
    health_provider_nit     VARCHAR(12)  NULL,
    identified_patient_uuid CHAR(36)     NULL,
    updated_at              DATETIME(6)  NOT NULL,

    CONSTRAINT pk_patient_references PRIMARY KEY (uuid),
    CONSTRAINT chk_patient_references_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_patient_references_version CHECK (source_version >= 0),
    CONSTRAINT chk_patient_references_sex CHECK (sex IN ('FEMALE', 'MALE', 'INDETERMINATE')),
    CONSTRAINT chk_patient_references_kind
        CHECK ((kind = 'REGISTERED'
                    AND document_type IS NOT NULL AND document_number IS NOT NULL AND first_names IS NOT NULL
                    AND last_names IS NOT NULL AND birth_date IS NOT NULL AND health_regime IS NOT NULL
                    AND code IS NULL AND estimated_birth_year IS NULL AND identified_patient_uuid IS NULL
                    AND status IN ('ACTIVE', 'INACTIVE', 'DECEASED'))
            OR (kind = 'UNIDENTIFIED'
                    AND code IS NOT NULL AND estimated_birth_year IS NOT NULL
                    AND document_type IS NULL AND document_number IS NULL AND first_names IS NULL
                    AND last_names IS NULL AND birth_date IS NULL AND health_regime IS NULL
                    AND status IN ('UNIDENTIFIED', 'IDENTIFIED', 'DECEASED'))),
    CONSTRAINT chk_patient_references_identification
        CHECK ((status = 'IDENTIFIED') = (identified_patient_uuid IS NOT NULL)),
    CONSTRAINT chk_patient_references_death CHECK ((status = 'DECEASED') = (date_of_death IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_patient_references_identified_patient ON patient_references (identified_patient_uuid);
