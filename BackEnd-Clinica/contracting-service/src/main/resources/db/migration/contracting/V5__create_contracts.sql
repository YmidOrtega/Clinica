CREATE TABLE contracts (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)      NOT NULL,
    version           BIGINT        NOT NULL,
    payer_id          BIGINT        NOT NULL,
    number            VARCHAR(40)   NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    modality          VARCHAR(20)   NOT NULL,
    valid_from        DATE          NOT NULL,
    valid_to          DATE          NULL,
    tariff_version_id BIGINT        NULL,
    tariff_factor     DECIMAL(6, 4) NULL,
    status            VARCHAR(20)   NOT NULL,
    status_reason     VARCHAR(500)  NULL,
    status_changed_at DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(36)   NULL,
    updated_at        DATETIME(6)   NOT NULL,
    updated_by        VARCHAR(36)   NULL,

    CONSTRAINT pk_contracts PRIMARY KEY (id),
    CONSTRAINT uk_contracts_uuid UNIQUE (uuid),
    CONSTRAINT uk_contracts_number UNIQUE (payer_id, number),
    CONSTRAINT fk_contracts_payer FOREIGN KEY (payer_id) REFERENCES payers (id),
    CONSTRAINT fk_contracts_tariff_version FOREIGN KEY (tariff_version_id) REFERENCES tariff_manual_versions (id),

    CONSTRAINT chk_contracts_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_contracts_version CHECK (version >= 0),
    CONSTRAINT chk_contracts_number CHECK (REGEXP_LIKE(number, '^[A-Z0-9][A-Z0-9./-]{1,39}$', 'c')),
    CONSTRAINT chk_contracts_name CHECK (CHAR_LENGTH(TRIM(name)) >= 3),
    CONSTRAINT chk_contracts_modality CHECK (modality IN ('EVENT', 'PACKAGE', 'CAPITATION', 'GLOBAL_BUDGET')),
    CONSTRAINT chk_contracts_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT chk_contracts_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT chk_contracts_status_details
        CHECK ((status IN ('DRAFT', 'ACTIVE') AND status_reason IS NULL)
            OR (status IN ('SUSPENDED', 'TERMINATED') AND CHAR_LENGTH(status_reason) >= 10)),
    CONSTRAINT chk_contracts_status_changed_at CHECK (status = 'DRAFT' OR status_changed_at IS NOT NULL),
    CONSTRAINT chk_contracts_tariff_factor
        CHECK (tariff_factor IS NULL OR (tariff_factor >= 0.1 AND tariff_factor <= 10)),
    CONSTRAINT chk_contracts_tariff_terms
        CHECK ((tariff_version_id IS NULL AND tariff_factor IS NULL)
            OR (tariff_version_id IS NOT NULL AND tariff_factor IS NOT NULL)),
    CONSTRAINT chk_contracts_priced_modality
        CHECK (modality IN ('EVENT', 'PACKAGE') OR tariff_version_id IS NULL),
    CONSTRAINT chk_contracts_active_with_terms
        CHECK (status <> 'ACTIVE' OR modality NOT IN ('EVENT', 'PACKAGE') OR tariff_version_id IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_contracts_in_force ON contracts (payer_id, status, valid_from, valid_to);

CREATE TABLE contract_tariff_exceptions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)       NOT NULL,
    contract_id       BIGINT         NOT NULL,
    cups_code         VARCHAR(8)     NOT NULL,
    agreed_price      DECIMAL(15, 2) NOT NULL,
    reason            VARCHAR(500)   NOT NULL,
    valid_from        DATE           NOT NULL,
    revoked_from      DATE           NULL,
    revocation_reason VARCHAR(500)   NULL,
    registered_at     DATETIME(6)    NOT NULL,
    registered_by     VARCHAR(36)    NULL,
    revoked_at        DATETIME(6)    NULL,
    revoked_by        VARCHAR(36)    NULL,

    CONSTRAINT pk_contract_tariff_exceptions PRIMARY KEY (id),
    CONSTRAINT uk_contract_tariff_exceptions_uuid UNIQUE (uuid),
    CONSTRAINT fk_contract_tariff_exceptions_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),

    CONSTRAINT chk_contract_tariff_exceptions_cups CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c')),
    CONSTRAINT chk_contract_tariff_exceptions_price CHECK (agreed_price >= 0),
    CONSTRAINT chk_contract_tariff_exceptions_reason CHECK (CHAR_LENGTH(TRIM(reason)) >= 10),
    CONSTRAINT chk_contract_tariff_exceptions_revocation
        CHECK ((revoked_from IS NULL AND revocation_reason IS NULL AND revoked_at IS NULL)
            OR (revoked_from IS NOT NULL AND revoked_from >= valid_from
                    AND CHAR_LENGTH(TRIM(revocation_reason)) >= 10 AND revoked_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_contract_tariff_exceptions_lookup
    ON contract_tariff_exceptions (contract_id, cups_code, valid_from, revoked_from);

CREATE TABLE contract_packages (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    uuid          CHAR(36)       NOT NULL,
    contract_id   BIGINT         NOT NULL,
    code          VARCHAR(20)    NOT NULL,
    name          VARCHAR(200)   NOT NULL,
    price         DECIMAL(15, 2) NOT NULL,
    valid_from    DATE           NOT NULL,
    revoked_from  DATE           NULL,
    registered_at DATETIME(6)    NOT NULL,
    registered_by VARCHAR(36)    NULL,
    revoked_at    DATETIME(6)    NULL,
    revoked_by    VARCHAR(36)    NULL,

    CONSTRAINT pk_contract_packages PRIMARY KEY (id),
    CONSTRAINT uk_contract_packages_uuid UNIQUE (uuid),
    CONSTRAINT fk_contract_packages_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),

    CONSTRAINT chk_contract_packages_code CHECK (REGEXP_LIKE(code, '^[A-Z0-9][A-Z0-9.-]{1,19}$', 'c')),
    CONSTRAINT chk_contract_packages_name CHECK (CHAR_LENGTH(TRIM(name)) >= 3),
    CONSTRAINT chk_contract_packages_price CHECK (price > 0),
    CONSTRAINT chk_contract_packages_revocation
        CHECK ((revoked_from IS NULL AND revoked_at IS NULL)
            OR (revoked_from IS NOT NULL AND revoked_from >= valid_from AND revoked_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_contract_packages_lookup ON contract_packages (contract_id, valid_from, revoked_from);

CREATE TABLE contract_package_items (
    package_id BIGINT     NOT NULL,
    cups_code  VARCHAR(8) NOT NULL,

    CONSTRAINT pk_contract_package_items PRIMARY KEY (package_id, cups_code),
    CONSTRAINT fk_contract_package_items_package FOREIGN KEY (package_id) REFERENCES contract_packages (id),

    CONSTRAINT chk_contract_package_items_cups CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE contracting_history.contracts_aud (
    id                BIGINT        NOT NULL,
    rev               BIGINT        NOT NULL,
    revtype           TINYINT       NOT NULL,
    uuid              CHAR(36)      NULL,
    payer_id          BIGINT        NULL,
    number            VARCHAR(40)   NULL,
    name              VARCHAR(200)  NULL,
    modality          VARCHAR(20)   NULL,
    valid_from        DATE          NULL,
    valid_to          DATE          NULL,
    tariff_version_id BIGINT        NULL,
    tariff_factor     DECIMAL(6, 4) NULL,
    status            VARCHAR(20)   NULL,
    status_reason     VARCHAR(500)  NULL,
    status_changed_at DATETIME(6)   NULL,
    created_at        DATETIME(6)   NULL,
    created_by        VARCHAR(36)   NULL,
    updated_at        DATETIME(6)   NULL,
    updated_by        VARCHAR(36)   NULL,

    CONSTRAINT pk_contracts_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_contracts_aud_revision FOREIGN KEY (rev) REFERENCES contracting_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
