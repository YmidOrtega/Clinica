ALTER TABLE invoices
    ADD COLUMN dian_status     VARCHAR(30)  NULL AFTER signed_at,
    ADD COLUMN dian_status_at  DATETIME(6)  NULL AFTER dian_status,
    ADD COLUMN dian_file_name  VARCHAR(60)  NULL AFTER dian_status_at,
    ADD COLUMN dian_track_id   VARCHAR(100) NULL AFTER dian_file_name,
    ADD COLUMN dian_attempts   INT          NOT NULL DEFAULT 0 AFTER dian_track_id,
    ADD CONSTRAINT chk_invoices_dian_status
        CHECK (dian_status IS NULL OR dian_status IN ('AWAITING_VALIDATION', 'ACCEPTED', 'REJECTED')),
    ADD CONSTRAINT chk_invoices_dian_signed CHECK (dian_status IS NULL OR signed_at IS NOT NULL),
    ADD CONSTRAINT chk_invoices_dian_attempts CHECK (dian_attempts >= 0),
    ADD INDEX idx_invoices_dian_delivery (dian_status, signed_at, status_changed_at);

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN dian_status    VARCHAR(30)  NULL,
    ADD COLUMN dian_status_at DATETIME(6)  NULL,
    ADD COLUMN dian_file_name VARCHAR(60)  NULL,
    ADD COLUMN dian_track_id  VARCHAR(100) NULL,
    ADD COLUMN dian_attempts  INT          NULL;

ALTER TABLE invoice_documents
    DROP CONSTRAINT chk_invoice_documents_kind,
    ADD CONSTRAINT chk_invoice_documents_kind
        CHECK (kind IN ('UBL_UNSIGNED', 'UBL_SIGNED', 'DIAN_APPLICATION_RESPONSE'));

CREATE TABLE dian_file_counters (
    year       SMALLINT NOT NULL,
    next_value BIGINT   NOT NULL,

    CONSTRAINT pk_dian_file_counters PRIMARY KEY (year),
    CONSTRAINT chk_dian_file_counters_value CHECK (next_value BETWEEN 1 AND 4294967295)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE dian_verdicts (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_id         BIGINT        NOT NULL,
    operation          VARCHAR(30)   NOT NULL,
    file_name          VARCHAR(60)   NULL,
    track_id           VARCHAR(100)  NULL,
    outcome            VARCHAR(20)   NOT NULL,
    status_code        VARCHAR(10)   NULL,
    status_description VARCHAR(500)  NULL,
    errors             TEXT          NULL,
    received_at        DATETIME(6)   NOT NULL,

    CONSTRAINT pk_dian_verdicts PRIMARY KEY (id),
    CONSTRAINT fk_dian_verdicts_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_dian_verdicts_outcome CHECK (outcome IN ('RECEIVED', 'PROCESSING', 'ACCEPTED', 'REJECTED')),
    INDEX idx_dian_verdicts_invoice (invoice_id, received_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
