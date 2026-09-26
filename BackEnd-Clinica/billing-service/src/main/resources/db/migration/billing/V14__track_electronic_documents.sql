CREATE TABLE electronic_documents (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    uuid           CHAR(36)     NOT NULL,
    version        BIGINT       NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    invoice_id     BIGINT       NOT NULL,
    number         VARCHAR(24)  NOT NULL,
    document_key   VARCHAR(96)  NOT NULL,
    issued_at      DATETIME(6)  NOT NULL,
    signed_at      DATETIME(6)  NULL,
    dian_status    VARCHAR(30)  NULL,
    dian_status_at DATETIME(6)  NULL,
    dian_file_name VARCHAR(60)  NULL,
    dian_track_id  VARCHAR(100) NULL,
    dian_attempts  INT          NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,

    CONSTRAINT pk_electronic_documents PRIMARY KEY (id),
    CONSTRAINT uk_electronic_documents_uuid UNIQUE (uuid),
    CONSTRAINT uk_electronic_documents_key UNIQUE (document_key),
    CONSTRAINT uk_electronic_documents_number UNIQUE (type, number),
    CONSTRAINT fk_electronic_documents_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_electronic_documents_type CHECK (type IN ('INVOICE', 'CREDIT_NOTE')),
    CONSTRAINT chk_electronic_documents_key CHECK (REGEXP_LIKE(document_key, '^[0-9a-f]{96}$', 'c')),
    CONSTRAINT chk_electronic_documents_dian_status
        CHECK (dian_status IS NULL OR dian_status IN ('AWAITING_VALIDATION', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_electronic_documents_dian_signed CHECK (dian_status IS NULL OR signed_at IS NOT NULL),
    CONSTRAINT chk_electronic_documents_dian_attempts CHECK (dian_attempts >= 0),
    INDEX idx_electronic_documents_signature (signed_at, issued_at),
    INDEX idx_electronic_documents_dian (dian_status, signed_at, issued_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.electronic_documents_aud (
    id             BIGINT       NOT NULL,
    rev            BIGINT       NOT NULL,
    revtype        TINYINT      NOT NULL,
    uuid           CHAR(36)     NULL,
    type           VARCHAR(20)  NULL,
    invoice_id     BIGINT       NULL,
    number         VARCHAR(24)  NULL,
    document_key   VARCHAR(96)  NULL,
    issued_at      DATETIME(6)  NULL,
    signed_at      DATETIME(6)  NULL,
    dian_status    VARCHAR(30)  NULL,
    dian_status_at DATETIME(6)  NULL,
    dian_file_name VARCHAR(60)  NULL,
    dian_track_id  VARCHAR(100) NULL,
    dian_attempts  INT          NULL,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,

    CONSTRAINT pk_electronic_documents_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_electronic_documents_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO electronic_documents (uuid, version, type, invoice_id, number, document_key, issued_at, signed_at,
                                  dian_status, dian_status_at, dian_file_name, dian_track_id, dian_attempts,
                                  created_at, updated_at)
SELECT UUID(), 0, 'INVOICE', id, number, cufe, status_changed_at, signed_at, dian_status, dian_status_at,
       dian_file_name, dian_track_id, dian_attempts, status_changed_at, updated_at
FROM invoices
WHERE number IS NOT NULL;

CREATE TABLE document_files (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    electronic_document_id BIGINT      NOT NULL,
    kind                   VARCHAR(30) NOT NULL,
    content                MEDIUMTEXT  NOT NULL,
    sha256                 VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,

    CONSTRAINT pk_document_files PRIMARY KEY (id),
    CONSTRAINT uk_document_files_kind UNIQUE (electronic_document_id, kind),
    CONSTRAINT fk_document_files_document FOREIGN KEY (electronic_document_id) REFERENCES electronic_documents (id),
    CONSTRAINT chk_document_files_kind CHECK (kind IN ('UBL_UNSIGNED', 'UBL_SIGNED', 'DIAN_APPLICATION_RESPONSE')),
    CONSTRAINT chk_document_files_sha256 CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO document_files (electronic_document_id, kind, content, sha256, created_at)
SELECT e.id, d.kind, d.content, d.sha256, d.created_at
FROM invoice_documents d
JOIN electronic_documents e ON e.invoice_id = d.invoice_id AND e.type = 'INVOICE';

DROP TABLE invoice_documents;

ALTER TABLE dian_verdicts
    ADD COLUMN electronic_document_id BIGINT NULL AFTER id;

UPDATE dian_verdicts v
JOIN electronic_documents e ON e.invoice_id = v.invoice_id AND e.type = 'INVOICE'
SET v.electronic_document_id = e.id;

ALTER TABLE dian_verdicts
    DROP FOREIGN KEY fk_dian_verdicts_invoice,
    DROP INDEX idx_dian_verdicts_invoice,
    DROP COLUMN invoice_id,
    MODIFY COLUMN electronic_document_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_dian_verdicts_document FOREIGN KEY (electronic_document_id) REFERENCES electronic_documents (id),
    ADD INDEX idx_dian_verdicts_document (electronic_document_id, received_at);

ALTER TABLE invoices
    DROP CONSTRAINT chk_invoices_signed,
    DROP CONSTRAINT chk_invoices_dian_status,
    DROP CONSTRAINT chk_invoices_dian_signed,
    DROP CONSTRAINT chk_invoices_dian_attempts,
    DROP INDEX idx_invoices_awaiting_signature,
    DROP INDEX idx_invoices_dian_delivery,
    DROP COLUMN signed_at,
    DROP COLUMN dian_status,
    DROP COLUMN dian_status_at,
    DROP COLUMN dian_file_name,
    DROP COLUMN dian_track_id,
    DROP COLUMN dian_attempts;

ALTER TABLE billing_history.invoices_aud
    DROP COLUMN signed_at,
    DROP COLUMN dian_status,
    DROP COLUMN dian_status_at,
    DROP COLUMN dian_file_name,
    DROP COLUMN dian_track_id,
    DROP COLUMN dian_attempts;
