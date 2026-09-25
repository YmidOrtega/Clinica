CREATE TABLE contract_authorization_requirements (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    contract_id       BIGINT       NOT NULL,
    cups_code         VARCHAR(8)   NOT NULL,
    valid_from        DATE         NOT NULL,
    revoked_from      DATE         NULL,
    revocation_reason VARCHAR(500) NULL,
    registered_at     DATETIME(6)  NOT NULL,
    registered_by     VARCHAR(36)  NULL,
    revoked_at        DATETIME(6)  NULL,
    revoked_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_contract_authorization_requirements PRIMARY KEY (id),
    CONSTRAINT uk_contract_authorization_requirements_uuid UNIQUE (uuid),
    CONSTRAINT fk_contract_authorization_requirements_contract FOREIGN KEY (contract_id) REFERENCES contracts (id),

    CONSTRAINT chk_contract_authorization_requirements_cups CHECK (REGEXP_LIKE(cups_code, '^[0-9]{6,8}$', 'c')),
    CONSTRAINT chk_contract_authorization_requirements_revocation
        CHECK ((revoked_from IS NULL AND revocation_reason IS NULL AND revoked_at IS NULL)
            OR (revoked_from IS NOT NULL AND revoked_from >= valid_from
                    AND CHAR_LENGTH(TRIM(revocation_reason)) >= 1 AND revoked_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_contract_authorization_requirements_lookup
    ON contract_authorization_requirements (contract_id, cups_code, valid_from, revoked_from);

ALTER TABLE contracting_outbox.outbox_events DROP CHECK chk_outbox_events_type;

ALTER TABLE contracting_outbox.outbox_events ADD CONSTRAINT chk_outbox_events_type CHECK (type IN (
    'ContractDrafted', 'ContractTariffTermsAgreed', 'ContractRenamed', 'ContractValidityChanged',
    'ContractActivated', 'ContractSuspended', 'ContractTerminated', 'ContractTariffExceptionRegistered',
    'ContractTariffExceptionRevoked', 'ContractPackageAgreed', 'ContractPackageRevoked',
    'ContractAuthorizationRequired', 'ContractAuthorizationRequirementRevoked',
    'TariffVersionPublished', 'TariffVersionRetired'));
