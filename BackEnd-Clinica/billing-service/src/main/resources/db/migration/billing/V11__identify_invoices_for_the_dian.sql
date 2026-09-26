ALTER TABLE invoices
    ADD COLUMN issued_time TIME        NULL AFTER issued_on,
    ADD COLUMN cufe        VARCHAR(96) NULL AFTER issued_time,
    ADD COLUMN qr_content  VARCHAR(1000) NULL AFTER cufe,
    ADD CONSTRAINT uk_invoices_cufe UNIQUE (cufe),
    ADD CONSTRAINT chk_invoices_cufe CHECK (cufe IS NULL OR REGEXP_LIKE(cufe, '^[0-9a-f]{96}$', 'c')),
    ADD CONSTRAINT chk_invoices_identified
        CHECK (status <> 'ISSUED' OR (cufe IS NOT NULL AND qr_content IS NOT NULL AND issued_time IS NOT NULL));

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN issued_time TIME          NULL,
    ADD COLUMN cufe        VARCHAR(96)   NULL,
    ADD COLUMN qr_content  VARCHAR(1000) NULL;

CREATE TABLE invoice_documents (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT      NOT NULL,
    kind       VARCHAR(30) NOT NULL,
    content    MEDIUMTEXT  NOT NULL,
    sha256     VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_invoice_documents PRIMARY KEY (id),
    CONSTRAINT uk_invoice_documents_kind UNIQUE (invoice_id, kind),
    CONSTRAINT fk_invoice_documents_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_invoice_documents_kind CHECK (kind IN ('UBL_UNSIGNED')),
    CONSTRAINT chk_invoice_documents_sha256 CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
