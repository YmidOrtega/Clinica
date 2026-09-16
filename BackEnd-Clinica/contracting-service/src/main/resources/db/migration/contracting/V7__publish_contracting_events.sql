CREATE DATABASE IF NOT EXISTS contracting_outbox
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE contracting_outbox.outbox_events (
    id            CHAR(36)    NOT NULL,
    aggregatetype VARCHAR(50) NOT NULL,
    aggregateid   CHAR(36)    NOT NULL,
    type          VARCHAR(80) NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype
        CHECK (aggregatetype IN ('contracting.contracts', 'contracting.tariffs')),
    CONSTRAINT chk_outbox_events_type CHECK (type IN (
        'ContractDrafted', 'ContractTariffTermsAgreed', 'ContractRenamed', 'ContractValidityChanged',
        'ContractActivated', 'ContractSuspended', 'ContractTerminated', 'ContractTariffExceptionRegistered',
        'ContractTariffExceptionRevoked', 'ContractPackageAgreed', 'ContractPackageRevoked',
        'TariffVersionPublished', 'TariffVersionRetired'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_outbox_events_created_at ON contracting_outbox.outbox_events (created_at);
