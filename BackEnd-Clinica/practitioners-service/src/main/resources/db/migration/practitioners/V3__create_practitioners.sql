CREATE TABLE practitioners (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                CHAR(36)     NOT NULL,
    version             BIGINT       NOT NULL,
    document_type       VARCHAR(40)  NOT NULL,
    document_number     VARCHAR(20)  NOT NULL,
    first_names         VARCHAR(100) NOT NULL,
    last_names          VARCHAR(100) NOT NULL,
    registration_number VARCHAR(30)  NOT NULL,
    registered_on       DATE         NULL,
    email               VARCHAR(150) NOT NULL,
    mobile              VARCHAR(20)  NOT NULL,
    phone               VARCHAR(20)  NULL,
    relationship        VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    status_reason       VARCHAR(500) NULL,
    status_changed_at   DATETIME(6)  NULL,
    specialties_agreed_at DATETIME(6) NULL,
    created_at          DATETIME(6)  NOT NULL,
    created_by          VARCHAR(36)  NULL,
    updated_at          DATETIME(6)  NOT NULL,
    updated_by          VARCHAR(36)  NULL,

    CONSTRAINT pk_practitioners PRIMARY KEY (id),
    CONSTRAINT uk_practitioners_uuid UNIQUE (uuid),
    CONSTRAINT uk_practitioners_document UNIQUE (document_type, document_number),
    CONSTRAINT uk_practitioners_registration UNIQUE (registration_number),
    CONSTRAINT uk_practitioners_email UNIQUE (email),

    CONSTRAINT chk_practitioners_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_practitioners_version CHECK (version >= 0),
    CONSTRAINT chk_practitioners_document_type
        CHECK (document_type IN ('CEDULA_DE_CIUDADANIA', 'CEDULA_DE_EXTRANJERIA', 'PASAPORTE',
                                 'PERMISO_ESPECIAL_DE_PERMANENCIA', 'PERMISO_POR_PROTECCION_TEMPORAL',
                                 'DOCUMENTO_EXTRANJERO')),
    CONSTRAINT chk_practitioners_document_number CHECK (REGEXP_LIKE(document_number, '^[A-Z0-9-]{4,20}$', 'c')),
    CONSTRAINT chk_practitioners_first_names CHECK (CHAR_LENGTH(TRIM(first_names)) >= 2),
    CONSTRAINT chk_practitioners_last_names CHECK (CHAR_LENGTH(TRIM(last_names)) >= 2),
    CONSTRAINT chk_practitioners_registration_number CHECK (REGEXP_LIKE(registration_number, '^[A-Z0-9][A-Z0-9-]{4,29}$', 'c')),
    CONSTRAINT chk_practitioners_email CHECK (REGEXP_LIKE(email, '^[^@[:space:]]+@[^@[:space:].]+(\\.[^@[:space:].]+)+$', 'c')),
    CONSTRAINT chk_practitioners_mobile CHECK (REGEXP_LIKE(mobile, '^3[0-9]{9}$', 'c')),
    CONSTRAINT chk_practitioners_phone CHECK (phone IS NULL OR REGEXP_LIKE(phone, '^[0-9]{7,12}$', 'c')),
    CONSTRAINT chk_practitioners_relationship CHECK (relationship IN ('STAFF', 'CONTRACTOR', 'EXTERNAL')),
    CONSTRAINT chk_practitioners_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT chk_practitioners_status_details
        CHECK ((status = 'ACTIVE' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status <> 'ACTIVE' AND CHAR_LENGTH(status_reason) >= 10 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_practitioners_last_names ON practitioners (last_names, first_names);

CREATE TABLE practitioner_specialties (
    id               BIGINT  NOT NULL AUTO_INCREMENT,
    practitioner_id  BIGINT  NOT NULL,
    specialty_id     BIGINT  NOT NULL,
    sub_specialty_id BIGINT  NULL,
    principal        BOOLEAN NOT NULL,

    CONSTRAINT pk_practitioner_specialties PRIMARY KEY (id),
    CONSTRAINT uk_practitioner_specialties UNIQUE (practitioner_id, specialty_id, sub_specialty_id),
    CONSTRAINT fk_practitioner_specialties_practitioner FOREIGN KEY (practitioner_id) REFERENCES practitioners (id),
    CONSTRAINT fk_practitioner_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id),
    CONSTRAINT fk_practitioner_specialties_sub_specialty FOREIGN KEY (sub_specialty_id) REFERENCES sub_specialties (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_practitioner_specialties_specialty ON practitioner_specialties (specialty_id);

CREATE TABLE practitioners_history.practitioners_aud (
    id                  BIGINT       NOT NULL,
    rev                 BIGINT       NOT NULL,
    revtype             TINYINT      NOT NULL,
    uuid                CHAR(36)     NULL,
    document_type       VARCHAR(40)  NULL,
    document_number     VARCHAR(20)  NULL,
    first_names         VARCHAR(100) NULL,
    last_names          VARCHAR(100) NULL,
    registration_number VARCHAR(30)  NULL,
    registered_on       DATE         NULL,
    email               VARCHAR(150) NULL,
    mobile              VARCHAR(20)  NULL,
    phone               VARCHAR(20)  NULL,
    relationship        VARCHAR(20)  NULL,
    status              VARCHAR(20)  NULL,
    status_reason       VARCHAR(500) NULL,
    status_changed_at   DATETIME(6)  NULL,
    specialties_agreed_at DATETIME(6) NULL,
    created_at          DATETIME(6)  NULL,
    created_by          VARCHAR(36)  NULL,
    updated_at          DATETIME(6)  NULL,
    updated_by          VARCHAR(36)  NULL,

    CONSTRAINT pk_practitioners_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_practitioners_aud_revision FOREIGN KEY (rev) REFERENCES practitioners_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE practitioners_history.practitioner_specialties_aud (
    id               BIGINT  NOT NULL,
    rev              BIGINT  NOT NULL,
    revtype          TINYINT NOT NULL,
    practitioner_id  BIGINT  NULL,
    specialty_id     BIGINT  NULL,
    sub_specialty_id BIGINT  NULL,
    principal        BOOLEAN NULL,

    CONSTRAINT pk_practitioner_specialties_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_practitioner_specialties_aud_revision FOREIGN KEY (rev) REFERENCES practitioners_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
