CREATE TABLE funding_agreements (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    uuid             CHAR(36)       NOT NULL,
    contract_id      BIGINT         NOT NULL,
    per_capita_value DECIMAL(15, 2) NULL,
    budget_ceiling   DECIMAL(18, 2) NULL,
    periodicity      VARCHAR(20)    NOT NULL,
    technical_note   VARCHAR(1000)  NOT NULL,
    valid_from       DATE           NOT NULL,
    revoked_from     DATE           NULL,
    registered_at    DATETIME(6)    NOT NULL,
    registered_by    VARCHAR(36)    NULL,
    revoked_at       DATETIME(6)    NULL,
    revoked_by       VARCHAR(36)    NULL,

    CONSTRAINT pk_funding_agreements PRIMARY KEY (id),
    CONSTRAINT uk_funding_agreements_uuid UNIQUE (uuid),
    CONSTRAINT fk_funding_agreements_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),

    CONSTRAINT chk_funding_agreements_periodicity CHECK (periodicity IN ('MONTHLY', 'BIMONTHLY', 'QUARTERLY')),
    CONSTRAINT chk_funding_agreements_note CHECK (CHAR_LENGTH(TRIM(technical_note)) >= 10),
    CONSTRAINT chk_funding_agreements_amount
        CHECK ((per_capita_value IS NOT NULL AND per_capita_value > 0 AND budget_ceiling IS NULL)
            OR (budget_ceiling IS NOT NULL AND budget_ceiling > 0 AND per_capita_value IS NULL)),
    CONSTRAINT chk_funding_agreements_revocation
        CHECK ((revoked_from IS NULL AND revoked_at IS NULL)
            OR (revoked_from IS NOT NULL AND revoked_from >= valid_from AND revoked_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_funding_agreements_lookup ON funding_agreements (contract_id, valid_from, revoked_from);

CREATE TABLE capitated_members (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    uuid            CHAR(36)     NOT NULL,
    contract_id     BIGINT       NOT NULL,
    period          DATE         NOT NULL,
    document_type   VARCHAR(20)  NOT NULL,
    document_number VARCHAR(20)  NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    patient_uuid    CHAR(36)     NULL,
    verification    VARCHAR(20)  NOT NULL,
    verified_at     DATETIME(6)  NULL,
    registered_at   DATETIME(6)  NOT NULL,
    registered_by   VARCHAR(36)  NULL,

    CONSTRAINT pk_capitated_members PRIMARY KEY (id),
    CONSTRAINT uk_capitated_members_uuid UNIQUE (uuid),
    CONSTRAINT uk_capitated_members_document UNIQUE (contract_id, period, document_type, document_number),
    CONSTRAINT fk_capitated_members_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),

    CONSTRAINT chk_capitated_members_period CHECK (DAYOFMONTH(period) = 1),
    CONSTRAINT chk_capitated_members_document_type CHECK (REGEXP_LIKE(document_type, '^[A-Z]{2,20}$', 'c')),
    CONSTRAINT chk_capitated_members_document_number CHECK (REGEXP_LIKE(document_number, '^[A-Z0-9-]{3,20}$', 'c')),
    CONSTRAINT chk_capitated_members_verification CHECK (verification IN ('MATCHED', 'UNMATCHED', 'UNVERIFIED')),
    CONSTRAINT chk_capitated_members_match
        CHECK ((verification = 'MATCHED' AND patient_uuid IS NOT NULL AND verified_at IS NOT NULL)
            OR (verification = 'UNMATCHED' AND patient_uuid IS NULL AND verified_at IS NOT NULL)
            OR (verification = 'UNVERIFIED' AND patient_uuid IS NULL AND verified_at IS NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_capitated_members_coverage ON capitated_members (document_type, document_number, period);
CREATE INDEX idx_capitated_members_verification ON capitated_members (contract_id, period, verification);
