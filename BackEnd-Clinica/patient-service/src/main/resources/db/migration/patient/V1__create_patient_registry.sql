CREATE TABLE patients (
    id                             BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                           CHAR(36)     NOT NULL,
    version                        BIGINT       NOT NULL,
    document_type                  VARCHAR(40)  NOT NULL,
    document_number                VARCHAR(20)  NOT NULL,
    first_names                    VARCHAR(100) NOT NULL,
    last_names                     VARCHAR(100) NOT NULL,
    birth_date                     DATE         NOT NULL,
    sex                            VARCHAR(20)  NOT NULL,
    country_of_origin              CHAR(2)      NOT NULL,
    disability                     VARCHAR(20)  NOT NULL,
    mobile                         VARCHAR(16)  NOT NULL,
    phone                          VARCHAR(16)  NULL,
    email                          VARCHAR(150) NULL,
    emergency_contact_name         VARCHAR(150) NULL,
    emergency_contact_relationship VARCHAR(20)  NULL,
    emergency_contact_phone        VARCHAR(16)  NULL,
    health_regime                  VARCHAR(20)  NOT NULL,
    affiliate_type                 VARCHAR(20)  NULL,
    health_provider_nit            VARCHAR(12)  NULL,
    policy_number                  VARCHAR(50)  NULL,
    residence_department           VARCHAR(100) NOT NULL,
    residence_municipality         VARCHAR(100) NOT NULL,
    residence_zone                 VARCHAR(10)  NOT NULL,
    residence_address              VARCHAR(255) NOT NULL,
    status                         VARCHAR(20)  NOT NULL,
    status_reason                  VARCHAR(500) NULL,
    status_changed_at              DATETIME(6)  NULL,
    date_of_death                  DATE         NULL,
    created_at                     DATETIME(6)  NOT NULL,
    created_by                     VARCHAR(36)  NULL,
    updated_at                     DATETIME(6)  NOT NULL,
    updated_by                     VARCHAR(36)  NULL,

    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT uk_patients_uuid UNIQUE (uuid),
    CONSTRAINT uk_patients_document UNIQUE (document_type, document_number),

    CONSTRAINT chk_patients_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_patients_version CHECK (version >= 0),
    CONSTRAINT chk_patients_document_type
        CHECK (document_type IN ('REGISTRO_CIVIL', 'TARJETA_DE_IDENTIDAD', 'CEDULA_DE_CIUDADANIA', 'CEDULA_DE_EXTRANJERIA',
                                 'PASAPORTE', 'PERMISO_ESPECIAL_DE_PERMANENCIA', 'PERMISO_POR_PROTECCION_TEMPORAL',
                                 'DOCUMENTO_EXTRANJERO')),
    CONSTRAINT chk_patients_document_number
        CHECK (REGEXP_LIKE(document_number, '^[A-Z0-9]{3,20}$', 'c')),
    CONSTRAINT chk_patients_numeric_document_number
        CHECK (document_type NOT IN ('REGISTRO_CIVIL', 'TARJETA_DE_IDENTIDAD', 'CEDULA_DE_CIUDADANIA',
                                     'PERMISO_ESPECIAL_DE_PERMANENCIA', 'PERMISO_POR_PROTECCION_TEMPORAL')
               OR REGEXP_LIKE(document_number, '^[0-9]{3,15}$', 'c')),
    CONSTRAINT chk_patients_first_names CHECK (TRIM(first_names) <> ''),
    CONSTRAINT chk_patients_last_names CHECK (TRIM(last_names) <> ''),
    CONSTRAINT chk_patients_birth_date CHECK (birth_date >= '1900-01-01'),
    CONSTRAINT chk_patients_sex CHECK (sex IN ('FEMALE', 'MALE', 'INDETERMINATE')),
    CONSTRAINT chk_patients_country_of_origin CHECK (REGEXP_LIKE(country_of_origin, '^[A-Z]{2}$', 'c')),
    CONSTRAINT chk_patients_disability
        CHECK (disability IN ('NONE', 'PHYSICAL', 'VISUAL', 'HEARING', 'COGNITIVE', 'PSYCHOSOCIAL', 'MULTIPLE', 'OTHER')),
    CONSTRAINT chk_patients_mobile CHECK (REGEXP_LIKE(mobile, '^[+]?[0-9]{7,15}$')),
    CONSTRAINT chk_patients_phone CHECK (phone IS NULL OR REGEXP_LIKE(phone, '^[+]?[0-9]{7,15}$')),
    CONSTRAINT chk_patients_email CHECK (email IS NULL OR REGEXP_LIKE(email, '^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$')),
    CONSTRAINT chk_patients_emergency_contact_complete
        CHECK ((emergency_contact_name IS NULL AND emergency_contact_relationship IS NULL AND emergency_contact_phone IS NULL)
            OR (emergency_contact_name IS NOT NULL AND emergency_contact_relationship IS NOT NULL AND emergency_contact_phone IS NOT NULL)),
    CONSTRAINT chk_patients_emergency_contact_relationship
        CHECK (emergency_contact_relationship IS NULL
            OR emergency_contact_relationship IN ('MOTHER', 'FATHER', 'LEGAL_GUARDIAN', 'SPOUSE', 'PARTNER', 'CHILD', 'SIBLING',
                                                  'GRANDPARENT', 'OTHER_RELATIVE', 'OTHER')),
    CONSTRAINT chk_patients_emergency_contact_phone
        CHECK (emergency_contact_phone IS NULL OR REGEXP_LIKE(emergency_contact_phone, '^[+]?[0-9]{7,15}$')),
    CONSTRAINT chk_patients_health_regime CHECK (health_regime IN ('CONTRIBUTORY', 'SUBSIDIZED', 'SPECIAL', 'UNINSURED')),
    CONSTRAINT chk_patients_affiliate_type CHECK (affiliate_type IS NULL OR affiliate_type IN ('HOLDER', 'BENEFICIARY')),
    CONSTRAINT chk_patients_health_provider_nit
        CHECK (health_provider_nit IS NULL OR REGEXP_LIKE(health_provider_nit, '^[0-9]{9,10}(-[0-9])?$')),
    CONSTRAINT chk_patients_affiliation_consistency
        CHECK ((health_regime = 'UNINSURED' AND affiliate_type IS NULL AND health_provider_nit IS NULL AND policy_number IS NULL)
            OR (health_regime <> 'UNINSURED' AND affiliate_type IS NOT NULL AND health_provider_nit IS NOT NULL)),
    CONSTRAINT chk_patients_residence
        CHECK (TRIM(residence_department) <> '' AND TRIM(residence_municipality) <> '' AND TRIM(residence_address) <> ''),
    CONSTRAINT chk_patients_residence_zone CHECK (residence_zone IN ('URBAN', 'RURAL')),
    CONSTRAINT chk_patients_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DECEASED')),
    CONSTRAINT chk_patients_status_consistency
        CHECK ((status = 'ACTIVE' AND status_reason IS NULL AND date_of_death IS NULL)
            OR (status = 'INACTIVE' AND status_reason IS NOT NULL AND TRIM(status_reason) <> '' AND date_of_death IS NULL)
            OR (status = 'DECEASED' AND date_of_death IS NOT NULL AND status_reason IS NULL)),
    CONSTRAINT chk_patients_date_of_death CHECK (date_of_death IS NULL OR date_of_death >= birth_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_patients_names ON patients (last_names, first_names);
CREATE INDEX idx_patients_health_provider ON patients (health_provider_nit);

CREATE TABLE revisions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    revised_at DATETIME(6) NOT NULL,
    revised_by VARCHAR(36) NULL,

    CONSTRAINT pk_revisions PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE patients_aud (
    id                             BIGINT       NOT NULL,
    rev                            BIGINT       NOT NULL,
    revtype                        TINYINT      NOT NULL,
    uuid                           CHAR(36)     NULL,
    document_type                  VARCHAR(40)  NULL,
    document_number                VARCHAR(20)  NULL,
    first_names                    VARCHAR(100) NULL,
    last_names                     VARCHAR(100) NULL,
    birth_date                     DATE         NULL,
    sex                            VARCHAR(20)  NULL,
    country_of_origin              CHAR(2)      NULL,
    disability                     VARCHAR(20)  NULL,
    mobile                         VARCHAR(16)  NULL,
    phone                          VARCHAR(16)  NULL,
    email                          VARCHAR(150) NULL,
    emergency_contact_name         VARCHAR(150) NULL,
    emergency_contact_relationship VARCHAR(20)  NULL,
    emergency_contact_phone        VARCHAR(16)  NULL,
    health_regime                  VARCHAR(20)  NULL,
    affiliate_type                 VARCHAR(20)  NULL,
    health_provider_nit            VARCHAR(12)  NULL,
    policy_number                  VARCHAR(50)  NULL,
    residence_department           VARCHAR(100) NULL,
    residence_municipality         VARCHAR(100) NULL,
    residence_zone                 VARCHAR(10)  NULL,
    residence_address              VARCHAR(255) NULL,
    status                         VARCHAR(20)  NULL,
    status_reason                  VARCHAR(500) NULL,
    status_changed_at              DATETIME(6)  NULL,
    date_of_death                  DATE         NULL,
    created_at                     DATETIME(6)  NULL,
    created_by                     VARCHAR(36)  NULL,
    updated_at                     DATETIME(6)  NULL,
    updated_by                     VARCHAR(36)  NULL,

    CONSTRAINT pk_patients_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_patients_aud_revision FOREIGN KEY (rev) REFERENCES revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
