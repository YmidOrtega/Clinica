CREATE TABLE tariff_manuals (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    uuid       CHAR(36)     NOT NULL,
    version    BIGINT       NOT NULL,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    unit       VARCHAR(10)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    created_by VARCHAR(36)  NULL,
    updated_at DATETIME(6)  NOT NULL,
    updated_by VARCHAR(36)  NULL,

    CONSTRAINT pk_tariff_manuals PRIMARY KEY (id),
    CONSTRAINT uk_tariff_manuals_uuid UNIQUE (uuid),
    CONSTRAINT uk_tariff_manuals_code UNIQUE (code),

    CONSTRAINT chk_tariff_manuals_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_tariff_manuals_version CHECK (version >= 0),
    CONSTRAINT chk_tariff_manuals_code CHECK (REGEXP_LIKE(code, '^[A-Z][A-Z0-9_]{2,29}$', 'c')),
    CONSTRAINT chk_tariff_manuals_name CHECK (CHAR_LENGTH(TRIM(name)) >= 3),
    CONSTRAINT chk_tariff_manuals_unit CHECK (unit IN ('COP', 'SMLDV', 'UVB'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tariff_manual_versions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)       NOT NULL,
    version           BIGINT         NOT NULL,
    manual_id         BIGINT         NOT NULL,
    label             VARCHAR(30)    NOT NULL,
    unit_value        DECIMAL(15, 4) NOT NULL,
    valid_from        DATE           NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    status_changed_at DATETIME(6)    NULL,
    source_checksum   VARCHAR(64)    NULL,
    item_count        INT            NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(36)    NULL,
    updated_at        DATETIME(6)    NOT NULL,
    updated_by        VARCHAR(36)    NULL,

    CONSTRAINT pk_tariff_manual_versions PRIMARY KEY (id),
    CONSTRAINT uk_tariff_manual_versions_uuid UNIQUE (uuid),
    CONSTRAINT uk_tariff_manual_versions_label UNIQUE (manual_id, label),
    CONSTRAINT fk_tariff_manual_versions_manual FOREIGN KEY (manual_id) REFERENCES tariff_manuals (id),

    CONSTRAINT chk_tariff_manual_versions_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_tariff_manual_versions_version CHECK (version >= 0),
    CONSTRAINT chk_tariff_manual_versions_unit_value CHECK (unit_value > 0),
    CONSTRAINT chk_tariff_manual_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_tariff_manual_versions_status_details
        CHECK ((status = 'DRAFT' AND status_changed_at IS NULL)
            OR (status IN ('ACTIVE', 'RETIRED') AND status_changed_at IS NOT NULL AND item_count > 0)),
    CONSTRAINT chk_tariff_manual_versions_checksum
        CHECK (source_checksum IS NULL OR REGEXP_LIKE(source_checksum, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_tariff_manual_versions_item_count CHECK (item_count >= 0),
    CONSTRAINT chk_tariff_manual_versions_loaded
        CHECK ((source_checksum IS NULL AND item_count = 0) OR (source_checksum IS NOT NULL AND item_count > 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_tariff_manual_versions_in_force ON tariff_manual_versions (manual_id, status, valid_from);

CREATE TABLE tariff_items (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    manual_version_id BIGINT         NOT NULL,
    cups_code         VARCHAR(8)     NOT NULL,
    description       VARCHAR(300)   NOT NULL,
    value             DECIMAL(15, 4) NOT NULL,

    CONSTRAINT pk_tariff_items PRIMARY KEY (id),
    CONSTRAINT uk_tariff_items_code UNIQUE (manual_version_id, cups_code),
    CONSTRAINT fk_tariff_items_version FOREIGN KEY (manual_version_id) REFERENCES tariff_manual_versions (id),

    CONSTRAINT chk_tariff_items_cups_code CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c')),
    CONSTRAINT chk_tariff_items_value CHECK (value >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE contracting_history.tariff_manuals_aud (
    id         BIGINT       NOT NULL,
    rev        BIGINT       NOT NULL,
    revtype    TINYINT      NOT NULL,
    uuid       CHAR(36)     NULL,
    code       VARCHAR(30)  NULL,
    name       VARCHAR(200) NULL,
    unit       VARCHAR(10)  NULL,
    created_at DATETIME(6)  NULL,
    created_by VARCHAR(36)  NULL,
    updated_at DATETIME(6)  NULL,
    updated_by VARCHAR(36)  NULL,

    CONSTRAINT pk_tariff_manuals_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_tariff_manuals_aud_revision FOREIGN KEY (rev) REFERENCES contracting_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE contracting_history.tariff_manual_versions_aud (
    id                BIGINT         NOT NULL,
    rev               BIGINT         NOT NULL,
    revtype           TINYINT        NOT NULL,
    uuid              CHAR(36)       NULL,
    manual_id         BIGINT         NULL,
    label             VARCHAR(30)    NULL,
    unit_value        DECIMAL(15, 4) NULL,
    valid_from        DATE           NULL,
    status            VARCHAR(20)    NULL,
    status_changed_at DATETIME(6)    NULL,
    source_checksum   VARCHAR(64)    NULL,
    item_count        INT            NULL,
    created_at        DATETIME(6)    NULL,
    created_by        VARCHAR(36)    NULL,
    updated_at        DATETIME(6)    NULL,
    updated_by        VARCHAR(36)    NULL,

    CONSTRAINT pk_tariff_manual_versions_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_tariff_manual_versions_aud_revision FOREIGN KEY (rev) REFERENCES contracting_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
