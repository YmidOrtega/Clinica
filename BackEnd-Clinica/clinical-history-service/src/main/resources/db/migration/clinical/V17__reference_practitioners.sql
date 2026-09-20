CREATE TABLE practitioner_references (
    user_uuid           CHAR(36)     NOT NULL,
    practitioner_uuid   CHAR(36)     NOT NULL,
    source_version      BIGINT       NOT NULL,
    full_name           VARCHAR(220) NOT NULL,
    registration_number VARCHAR(30)  NOT NULL,
    specialty           VARCHAR(310) NULL,
    status              VARCHAR(20)  NOT NULL,
    account_linked      BOOLEAN      NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,

    CONSTRAINT pk_practitioner_references PRIMARY KEY (user_uuid),
    CONSTRAINT uk_practitioner_references_practitioner UNIQUE (practitioner_uuid),
    CONSTRAINT chk_practitioner_references_user_uuid
        CHECK (REGEXP_LIKE(user_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_practitioner_references_practitioner_uuid
        CHECK (REGEXP_LIKE(practitioner_uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_practitioner_references_version CHECK (source_version >= 0),
    CONSTRAINT chk_practitioner_references_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'RETIRED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
