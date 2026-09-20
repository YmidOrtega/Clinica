CREATE TABLE specialties (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL,
    code              VARCHAR(20)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_specialties PRIMARY KEY (id),
    CONSTRAINT uk_specialties_uuid UNIQUE (uuid),
    CONSTRAINT uk_specialties_code UNIQUE (code),

    CONSTRAINT chk_specialties_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_specialties_version CHECK (version >= 0),
    CONSTRAINT chk_specialties_code CHECK (REGEXP_LIKE(code, '^[A-Z0-9][A-Z0-9-]{1,19}$', 'c')),
    CONSTRAINT chk_specialties_name CHECK (CHAR_LENGTH(TRIM(name)) >= 3),
    CONSTRAINT chk_specialties_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_specialties_status_details
        CHECK ((status = 'ACTIVE' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status = 'INACTIVE' AND CHAR_LENGTH(status_reason) >= 10 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_specialties_name ON specialties (name);

CREATE TABLE sub_specialties (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL,
    specialty_id      BIGINT       NOT NULL,
    code              VARCHAR(20)  NOT NULL,
    name              VARCHAR(150) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_sub_specialties PRIMARY KEY (id),
    CONSTRAINT uk_sub_specialties_uuid UNIQUE (uuid),
    CONSTRAINT uk_sub_specialties_code UNIQUE (code),
    CONSTRAINT fk_sub_specialties_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id),

    CONSTRAINT chk_sub_specialties_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_sub_specialties_version CHECK (version >= 0),
    CONSTRAINT chk_sub_specialties_code CHECK (REGEXP_LIKE(code, '^[A-Z0-9][A-Z0-9-]{1,19}$', 'c')),
    CONSTRAINT chk_sub_specialties_name CHECK (CHAR_LENGTH(TRIM(name)) >= 3),
    CONSTRAINT chk_sub_specialties_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_sub_specialties_status_details
        CHECK ((status = 'ACTIVE' AND status_reason IS NULL AND status_changed_at IS NULL)
            OR (status = 'INACTIVE' AND CHAR_LENGTH(status_reason) >= 10 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_sub_specialties_specialty ON sub_specialties (specialty_id);

CREATE TABLE practitioners_history.specialties_aud (
    id                BIGINT       NOT NULL,
    rev               BIGINT       NOT NULL,
    revtype           TINYINT      NOT NULL,
    uuid              CHAR(36)     NULL,
    code              VARCHAR(20)  NULL,
    name              VARCHAR(150) NULL,
    status            VARCHAR(20)  NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_specialties_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_specialties_aud_revision FOREIGN KEY (rev) REFERENCES practitioners_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE practitioners_history.sub_specialties_aud (
    id                BIGINT       NOT NULL,
    rev               BIGINT       NOT NULL,
    revtype           TINYINT      NOT NULL,
    uuid              CHAR(36)     NULL,
    specialty_id      BIGINT       NULL,
    code              VARCHAR(20)  NULL,
    name              VARCHAR(150) NULL,
    status            VARCHAR(20)  NULL,
    status_reason     VARCHAR(500) NULL,
    status_changed_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_sub_specialties_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_sub_specialties_aud_revision FOREIGN KEY (rev) REFERENCES practitioners_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
