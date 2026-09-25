CREATE TABLE issuer (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    singleton            TINYINT      NOT NULL DEFAULT 1,
    uuid                 CHAR(36)     NOT NULL,
    version              BIGINT       NOT NULL,
    nit                  VARCHAR(10)  NOT NULL,
    verification_digit   INT          NOT NULL,
    person_type          VARCHAR(20)  NOT NULL,
    legal_name           VARCHAR(200) NOT NULL,
    trade_name           VARCHAR(200) NULL,
    tax_scheme           VARCHAR(20)  NOT NULL,
    tax_responsibilities VARCHAR(60)  NOT NULL,
    address_line         VARCHAR(200) NOT NULL,
    municipality_code    VARCHAR(5)   NOT NULL,
    city_name            VARCHAR(60)  NOT NULL,
    department_name      VARCHAR(60)  NOT NULL,
    postal_code          VARCHAR(6)   NULL,
    email                VARCHAR(254) NOT NULL,
    phone                VARCHAR(10)  NOT NULL,
    health_provider_code VARCHAR(12)  NOT NULL,
    environment          VARCHAR(20)  NOT NULL,
    production_since     DATETIME(6)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    created_by           VARCHAR(36)  NULL,
    updated_at           DATETIME(6)  NOT NULL,
    updated_by           VARCHAR(36)  NULL,

    CONSTRAINT pk_issuer PRIMARY KEY (id),
    CONSTRAINT uk_issuer_singleton UNIQUE (singleton),
    CONSTRAINT uk_issuer_uuid UNIQUE (uuid),

    CONSTRAINT chk_issuer_singleton CHECK (singleton = 1),
    CONSTRAINT chk_issuer_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_issuer_version CHECK (version >= 0),
    CONSTRAINT chk_issuer_nit CHECK (REGEXP_LIKE(nit, '^[1-9][0-9]{5,9}$', 'c')),
    CONSTRAINT chk_issuer_verification_digit CHECK (verification_digit BETWEEN 0 AND 9),
    CONSTRAINT chk_issuer_person_type CHECK (person_type IN ('LEGAL_ENTITY', 'NATURAL_PERSON')),
    CONSTRAINT chk_issuer_legal_name CHECK (CHAR_LENGTH(TRIM(legal_name)) >= 1),
    CONSTRAINT chk_issuer_tax_scheme CHECK (tax_scheme IN ('VAT', 'NOT_APPLICABLE')),
    CONSTRAINT chk_issuer_tax_responsibilities
        CHECK (tax_responsibilities = 'R-99-PN'
            OR REGEXP_LIKE(tax_responsibilities, '^(O-13|O-15|O-23|O-47)(;(O-13|O-15|O-23|O-47)){0,3}$', 'c')),
    CONSTRAINT chk_issuer_municipality_code CHECK (REGEXP_LIKE(municipality_code, '^[0-9]{5}$', 'c')),
    CONSTRAINT chk_issuer_postal_code CHECK (postal_code IS NULL OR REGEXP_LIKE(postal_code, '^[0-9]{6}$', 'c')),
    CONSTRAINT chk_issuer_email CHECK (REGEXP_LIKE(email, '^[^@[:space:]]+@[^@[:space:]]+[.][^@[:space:]]+$', 'c')),
    CONSTRAINT chk_issuer_phone CHECK (REGEXP_LIKE(phone, '^[0-9]{7,10}$', 'c')),
    CONSTRAINT chk_issuer_health_provider_code CHECK (REGEXP_LIKE(health_provider_code, '^[0-9]{10,12}$', 'c')),
    CONSTRAINT chk_issuer_environment
        CHECK ((environment = 'TEST' AND production_since IS NULL)
            OR (environment = 'PRODUCTION' AND production_since IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE numbering_resolutions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL,
    resolution_number VARCHAR(20)  NOT NULL,
    issued_on         DATE         NOT NULL,
    prefix            VARCHAR(4)   NOT NULL,
    range_from        BIGINT       NOT NULL,
    range_to          BIGINT       NOT NULL,
    valid_from        DATE         NOT NULL,
    valid_until       DATE         NOT NULL,
    technical_key     VARCHAR(100) NOT NULL,
    environment       VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    active_slot       TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 END) STORED,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_numbering_resolutions PRIMARY KEY (id),
    CONSTRAINT uk_numbering_resolutions_uuid UNIQUE (uuid),
    CONSTRAINT uk_numbering_resolutions_number UNIQUE (resolution_number, prefix),
    CONSTRAINT uk_numbering_resolutions_single_active UNIQUE (active_slot),

    CONSTRAINT chk_numbering_resolutions_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_numbering_resolutions_version CHECK (version >= 0),
    CONSTRAINT chk_numbering_resolutions_number CHECK (REGEXP_LIKE(resolution_number, '^[0-9]{1,20}$', 'c')),
    CONSTRAINT chk_numbering_resolutions_prefix CHECK (REGEXP_LIKE(prefix, '^[A-Z0-9]{0,4}$', 'c')),
    CONSTRAINT chk_numbering_resolutions_range CHECK (range_from >= 1 AND range_to >= range_from),
    CONSTRAINT chk_numbering_resolutions_validity CHECK (valid_until >= valid_from AND valid_from >= issued_on),
    CONSTRAINT chk_numbering_resolutions_technical_key
        CHECK (REGEXP_LIKE(technical_key, '^[0-9a-f]{20,100}$', 'c')),
    CONSTRAINT chk_numbering_resolutions_environment CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT chk_numbering_resolutions_status CHECK (status IN ('PENDING', 'ACTIVE', 'EXHAUSTED', 'RETIRED')),
    CONSTRAINT chk_numbering_resolutions_status_details
        CHECK ((status = 'PENDING' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status IN ('ACTIVE', 'EXHAUSTED') AND status_reason IS NULL AND status_changed_at IS NOT NULL)
            OR (status = 'RETIRED' AND CHAR_LENGTH(TRIM(status_reason)) >= 1 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_numbering_resolutions_prefix ON numbering_resolutions (prefix);

CREATE TABLE numbering_counters (
    resolution_id BIGINT NOT NULL,
    next_number   BIGINT NOT NULL,

    CONSTRAINT pk_numbering_counters PRIMARY KEY (resolution_id),
    CONSTRAINT fk_numbering_counters_resolution FOREIGN KEY (resolution_id) REFERENCES numbering_resolutions (id),
    CONSTRAINT chk_numbering_counters_next CHECK (next_number >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.issuer_aud (
    id                   BIGINT       NOT NULL,
    rev                  BIGINT       NOT NULL,
    revtype              TINYINT      NOT NULL,
    uuid                 CHAR(36)     NULL,
    nit                  VARCHAR(10)  NULL,
    verification_digit   INT          NULL,
    person_type          VARCHAR(20)  NULL,
    legal_name           VARCHAR(200) NULL,
    trade_name           VARCHAR(200) NULL,
    tax_scheme           VARCHAR(20)  NULL,
    tax_responsibilities VARCHAR(60)  NULL,
    address_line         VARCHAR(200) NULL,
    municipality_code    VARCHAR(5)   NULL,
    city_name            VARCHAR(60)  NULL,
    department_name      VARCHAR(60)  NULL,
    postal_code          VARCHAR(6)   NULL,
    email                VARCHAR(254) NULL,
    phone                VARCHAR(10)  NULL,
    health_provider_code VARCHAR(12)  NULL,
    environment          VARCHAR(20)  NULL,
    production_since     DATETIME(6)  NULL,
    created_at           DATETIME(6)  NULL,
    created_by           VARCHAR(36)  NULL,
    updated_at           DATETIME(6)  NULL,
    updated_by           VARCHAR(36)  NULL,

    CONSTRAINT pk_issuer_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_issuer_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.numbering_resolutions_aud (
    id                BIGINT       NOT NULL,
    rev               BIGINT       NOT NULL,
    revtype           TINYINT      NOT NULL,
    uuid              CHAR(36)     NULL,
    resolution_number VARCHAR(20)  NULL,
    issued_on         DATE         NULL,
    prefix            VARCHAR(4)   NULL,
    range_from        BIGINT       NULL,
    range_to          BIGINT       NULL,
    valid_from        DATE         NULL,
    valid_until       DATE         NULL,
    technical_key     VARCHAR(100) NULL,
    environment       VARCHAR(20)  NULL,
    status            VARCHAR(20)  NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_numbering_resolutions_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_numbering_resolutions_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
