CREATE TABLE invoice_filings (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)     NOT NULL,
    version           BIGINT       NOT NULL,
    invoice_id        BIGINT       NOT NULL,
    cuv               VARCHAR(96)  NOT NULL,
    filing_number     VARCHAR(60)  NOT NULL,
    filed_on          DATE         NOT NULL,
    deadline          DATE         NOT NULL,
    correction_reason VARCHAR(500) NULL,
    created_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_invoice_filings PRIMARY KEY (id),
    CONSTRAINT uk_invoice_filings_uuid UNIQUE (uuid),
    CONSTRAINT uk_invoice_filings_invoice UNIQUE (invoice_id),
    CONSTRAINT fk_invoice_filings_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_invoice_filings_cuv CHECK (REGEXP_LIKE(cuv, '^[0-9a-f]{96}$', 'c')),
    CONSTRAINT chk_invoice_filings_number CHECK (CHAR_LENGTH(TRIM(filing_number)) >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.invoice_filings_aud (
    id                BIGINT       NOT NULL,
    rev               BIGINT       NOT NULL,
    revtype           TINYINT      NOT NULL,
    uuid              CHAR(36)     NULL,
    invoice_id        BIGINT       NULL,
    cuv               VARCHAR(96)  NULL,
    filing_number     VARCHAR(60)  NULL,
    filed_on          DATE         NULL,
    deadline          DATE         NULL,
    correction_reason VARCHAR(500) NULL,
    created_at        DATETIME(6)  NULL,
    created_by        VARCHAR(36)  NULL,
    updated_at        DATETIME(6)  NULL,
    updated_by        VARCHAR(36)  NULL,

    CONSTRAINT pk_invoice_filings_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_invoice_filings_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_invoices_awaiting_filing ON invoices (purpose, buyer_kind, status, issued_on);
