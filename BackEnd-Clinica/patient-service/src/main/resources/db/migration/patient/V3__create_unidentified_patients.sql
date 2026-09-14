CREATE TABLE unidentified_patients (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    uuid                    CHAR(36)      NOT NULL,
    version                 BIGINT        NOT NULL,
    code                    VARCHAR(14)   NOT NULL,
    sex                     VARCHAR(20)   NOT NULL,
    estimated_birth_year    INT           NOT NULL,
    description             VARCHAR(1000) NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    identified_patient_uuid CHAR(36)      NULL,
    status_reason           VARCHAR(500)  NULL,
    status_changed_at       DATETIME(6)   NULL,
    date_of_death           DATE          NULL,
    created_at              DATETIME(6)   NOT NULL,
    created_by              VARCHAR(36)   NULL,
    updated_at              DATETIME(6)   NOT NULL,
    updated_by              VARCHAR(36)   NULL,

    CONSTRAINT pk_unidentified_patients PRIMARY KEY (id),
    CONSTRAINT uk_unidentified_patients_uuid UNIQUE (uuid),
    CONSTRAINT uk_unidentified_patients_code UNIQUE (code),
    CONSTRAINT fk_unidentified_patients_identified_patient
        FOREIGN KEY (identified_patient_uuid) REFERENCES patients (uuid),

    CONSTRAINT chk_unidentified_patients_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_unidentified_patients_code CHECK (REGEXP_LIKE(code, '^NN-[0-9]{4}-[0-9]{6}$', 'c')),
    CONSTRAINT chk_unidentified_patients_sex CHECK (sex IN ('FEMALE', 'MALE', 'INDETERMINATE')),
    CONSTRAINT chk_unidentified_patients_birth_year CHECK (estimated_birth_year >= 1900),
    CONSTRAINT chk_unidentified_patients_description CHECK (TRIM(description) <> ''),
    CONSTRAINT chk_unidentified_patients_status CHECK (status IN ('UNIDENTIFIED', 'IDENTIFIED', 'DECEASED')),
    CONSTRAINT chk_unidentified_patients_status_consistency
        CHECK ((status = 'UNIDENTIFIED' AND identified_patient_uuid IS NULL AND date_of_death IS NULL)
            OR (status = 'IDENTIFIED' AND identified_patient_uuid IS NOT NULL AND status_reason IS NOT NULL
                AND TRIM(status_reason) <> '' AND date_of_death IS NULL)
            OR (status = 'DECEASED' AND date_of_death IS NOT NULL AND identified_patient_uuid IS NULL)),
    CONSTRAINT chk_unidentified_patients_date_of_death
        CHECK (date_of_death IS NULL OR YEAR(date_of_death) >= estimated_birth_year)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_unidentified_patients_identified_patient ON unidentified_patients (identified_patient_uuid);

CREATE TABLE unidentified_patient_codes (
    code_year      SMALLINT NOT NULL,
    sequence_value INT      NOT NULL,

    CONSTRAINT pk_unidentified_patient_codes PRIMARY KEY (code_year),
    CONSTRAINT chk_unidentified_patient_codes_value CHECK (sequence_value BETWEEN 1 AND 999999)
) ENGINE = InnoDB;

CREATE TABLE unidentified_patients_aud (
    id                      BIGINT        NOT NULL,
    rev                     BIGINT        NOT NULL,
    revtype                 TINYINT       NOT NULL,
    uuid                    CHAR(36)      NULL,
    code                    VARCHAR(14)   NULL,
    sex                     VARCHAR(20)   NULL,
    estimated_birth_year    INT           NULL,
    description             VARCHAR(1000) NULL,
    status                  VARCHAR(20)   NULL,
    identified_patient_uuid CHAR(36)      NULL,
    status_reason           VARCHAR(500)  NULL,
    status_changed_at       DATETIME(6)   NULL,
    date_of_death           DATE          NULL,
    created_at              DATETIME(6)   NULL,
    created_by              VARCHAR(36)   NULL,
    updated_at              DATETIME(6)   NULL,
    updated_by              VARCHAR(36)   NULL,

    CONSTRAINT pk_unidentified_patients_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_unidentified_patients_aud_revision FOREIGN KEY (rev) REFERENCES revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE patient_outbox.outbox_events DROP CHECK chk_outbox_events_type;

ALTER TABLE patient_outbox.outbox_events
    ADD CONSTRAINT chk_outbox_events_type CHECK (type IN ('PatientRegistered', 'PatientDocumentChanged',
                                                          'PatientDemographicsCorrected', 'PatientAffiliationUpdated',
                                                          'PatientDeactivated', 'PatientReactivated', 'PatientDied',
                                                          'UnidentifiedPatientRegistered', 'UnidentifiedPatientIdentified',
                                                          'UnidentifiedPatientIdentificationReverted',
                                                          'UnidentifiedPatientDied'));
