CREATE TABLE fee_agreements (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    uuid            CHAR(36)      NOT NULL,
    practitioner_id BIGINT        NOT NULL,
    basis           VARCHAR(20)   NOT NULL,
    amount          DECIMAL(12,2) NULL,
    valid_from      DATE          NOT NULL,
    revoked_on      DATE          NULL,
    note            VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    created_by      VARCHAR(36)   NULL,

    CONSTRAINT pk_fee_agreements PRIMARY KEY (id),
    CONSTRAINT uk_fee_agreements_uuid UNIQUE (uuid),
    CONSTRAINT fk_fee_agreements_practitioner FOREIGN KEY (practitioner_id) REFERENCES practitioners (id),

    CONSTRAINT chk_fee_agreements_uuid
        CHECK (REGEXP_LIKE(uuid, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_fee_agreements_basis CHECK (basis IN ('HOURLY', 'PER_SHIFT', 'PER_PROCEDURE')),
    CONSTRAINT chk_fee_agreements_amount
        CHECK ((basis = 'PER_PROCEDURE' AND amount IS NULL)
            OR (basis <> 'PER_PROCEDURE' AND amount BETWEEN 1000 AND 100000000)),
    CONSTRAINT chk_fee_agreements_revoked_on CHECK (revoked_on IS NULL OR revoked_on > valid_from)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_fee_agreements_practitioner ON fee_agreements (practitioner_id, valid_from);

CREATE TABLE fee_agreement_lines (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    agreement_id BIGINT        NOT NULL,
    service_code VARCHAR(8)    NOT NULL,
    amount       DECIMAL(12,2) NOT NULL,

    CONSTRAINT pk_fee_agreement_lines PRIMARY KEY (id),
    CONSTRAINT uk_fee_agreement_lines UNIQUE (agreement_id, service_code),
    CONSTRAINT fk_fee_agreement_lines_agreement FOREIGN KEY (agreement_id) REFERENCES fee_agreements (id),

    CONSTRAINT chk_fee_agreement_lines_service_code CHECK (REGEXP_LIKE(service_code, '^[0-9]{6,8}$', 'c')),
    CONSTRAINT chk_fee_agreement_lines_amount CHECK (amount BETWEEN 1000 AND 100000000)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE practitioners_history.fee_agreements_aud (
    id              BIGINT        NOT NULL,
    rev             BIGINT        NOT NULL,
    revtype         TINYINT       NOT NULL,
    uuid            CHAR(36)      NULL,
    practitioner_id BIGINT        NULL,
    basis           VARCHAR(20)   NULL,
    amount          DECIMAL(12,2) NULL,
    valid_from      DATE          NULL,
    revoked_on      DATE          NULL,
    note            VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NULL,
    created_by      VARCHAR(36)   NULL,

    CONSTRAINT pk_fee_agreements_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_fee_agreements_aud_revision FOREIGN KEY (rev) REFERENCES practitioners_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE practitioners_history.fee_agreement_lines_aud (
    id           BIGINT        NOT NULL,
    rev          BIGINT        NOT NULL,
    revtype      TINYINT       NOT NULL,
    agreement_id BIGINT        NULL,
    service_code VARCHAR(8)    NULL,
    amount       DECIMAL(12,2) NULL,

    CONSTRAINT pk_fee_agreement_lines_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_fee_agreement_lines_aud_revision FOREIGN KEY (rev) REFERENCES practitioners_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
