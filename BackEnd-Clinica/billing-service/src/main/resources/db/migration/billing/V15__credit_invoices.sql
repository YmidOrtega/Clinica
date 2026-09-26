ALTER TABLE issuer
    ADD COLUMN credit_note_prefix VARCHAR(4) NOT NULL DEFAULT 'NC' AFTER health_provider_code,
    ADD CONSTRAINT chk_issuer_credit_note_prefix CHECK (REGEXP_LIKE(credit_note_prefix, '^[A-Z0-9]{1,4}$', 'c'));

ALTER TABLE billing_history.issuer_aud
    ADD COLUMN credit_note_prefix VARCHAR(4) NULL;

CREATE TABLE credit_note_counters (
    prefix      VARCHAR(4) NOT NULL,
    next_value  BIGINT     NOT NULL,

    CONSTRAINT pk_credit_note_counters PRIMARY KEY (prefix),
    CONSTRAINT chk_credit_note_counters_value CHECK (next_value >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE invoices
    ADD COLUMN credited_total DECIMAL(14,2) NOT NULL DEFAULT 0 AFTER payable_total,
    ADD CONSTRAINT chk_invoices_credited CHECK (credited_total >= 0 AND credited_total <= payable_total),
    DROP CONSTRAINT chk_invoices_status,
    DROP CONSTRAINT chk_invoices_numbering,
    ADD CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'DISCARDED', 'VOIDED')),
    ADD CONSTRAINT chk_invoices_numbering
        CHECK ((status IN ('ISSUED', 'VOIDED') AND number IS NOT NULL AND consecutive IS NOT NULL
                   AND resolution_uuid IS NOT NULL AND issued_on IS NOT NULL AND status_changed_at IS NOT NULL)
            OR (status IN ('DRAFT', 'DISCARDED') AND number IS NULL AND consecutive IS NULL)),
    ADD CONSTRAINT chk_invoices_void
        CHECK (status <> 'VOIDED' OR (CHAR_LENGTH(TRIM(status_reason)) >= 1 AND status_changed_at IS NOT NULL));

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN credited_total DECIMAL(14,2) NULL;

CREATE TABLE credit_notes (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    uuid              CHAR(36)      NOT NULL,
    version           BIGINT        NOT NULL,
    invoice_id        BIGINT        NOT NULL,
    concept           VARCHAR(30)   NOT NULL,
    reason            VARCHAR(500)  NOT NULL,
    prefix            VARCHAR(4)    NOT NULL,
    consecutive       BIGINT        NOT NULL,
    number            VARCHAR(24)   NOT NULL,
    issued_on         DATE          NOT NULL,
    issued_time       TIME          NOT NULL,
    issued_at         DATETIME(6)   NOT NULL,
    credited_gross    DECIMAL(14,2) NOT NULL,
    credited_share    DECIMAL(14,2) NOT NULL,
    credited_payable  DECIMAL(14,2) NOT NULL,
    cude              VARCHAR(96)   NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    created_by        VARCHAR(36)   NULL,

    CONSTRAINT pk_credit_notes PRIMARY KEY (id),
    CONSTRAINT uk_credit_notes_uuid UNIQUE (uuid),
    CONSTRAINT uk_credit_notes_number UNIQUE (number),
    CONSTRAINT uk_credit_notes_consecutive UNIQUE (prefix, consecutive),
    CONSTRAINT uk_credit_notes_cude UNIQUE (cude),
    CONSTRAINT fk_credit_notes_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_credit_notes_concept
        CHECK (concept IN ('PARTIAL_RETURN', 'VOID', 'DISCOUNT', 'PRICE_ADJUSTMENT')),
    CONSTRAINT chk_credit_notes_reason CHECK (CHAR_LENGTH(TRIM(reason)) >= 1),
    CONSTRAINT chk_credit_notes_totals
        CHECK (credited_gross > 0 AND credited_share >= 0 AND credited_payable >= 0
            AND credited_payable = credited_gross - credited_share
            AND (concept = 'VOID' OR credited_share = 0)),
    CONSTRAINT chk_credit_notes_cude CHECK (REGEXP_LIKE(cude, '^[0-9a-f]{96}$', 'c')),
    INDEX idx_credit_notes_invoice (invoice_id, issued_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.credit_notes_aud (
    id                BIGINT        NOT NULL,
    rev               BIGINT        NOT NULL,
    revtype           TINYINT       NOT NULL,
    uuid              CHAR(36)      NULL,
    invoice_id        BIGINT        NULL,
    concept           VARCHAR(30)   NULL,
    reason            VARCHAR(500)  NULL,
    prefix            VARCHAR(4)    NULL,
    consecutive       BIGINT        NULL,
    number            VARCHAR(24)   NULL,
    issued_on         DATE          NULL,
    issued_time       TIME          NULL,
    issued_at         DATETIME(6)   NULL,
    credited_gross    DECIMAL(14,2) NULL,
    credited_share    DECIMAL(14,2) NULL,
    credited_payable  DECIMAL(14,2) NULL,
    cude              VARCHAR(96)   NULL,
    created_at        DATETIME(6)   NULL,
    created_by        VARCHAR(36)   NULL,

    CONSTRAINT pk_credit_notes_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_credit_notes_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE credit_note_lines (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    credit_note_id        BIGINT        NOT NULL,
    position              INT           NOT NULL,
    invoice_line_position INT           NOT NULL,
    code                  VARCHAR(40)   NOT NULL,
    description           VARCHAR(300)  NOT NULL,
    quantity              INT           NOT NULL,
    unit_price            DECIMAL(14,2) NOT NULL,
    line_total            DECIMAL(14,2) NOT NULL,

    CONSTRAINT pk_credit_note_lines PRIMARY KEY (id),
    CONSTRAINT uk_credit_note_lines_position UNIQUE (credit_note_id, position),
    CONSTRAINT uk_credit_note_lines_invoice_line UNIQUE (credit_note_id, invoice_line_position),
    CONSTRAINT fk_credit_note_lines_note FOREIGN KEY (credit_note_id) REFERENCES credit_notes (id),
    CONSTRAINT chk_credit_note_lines_amounts CHECK (quantity >= 1 AND unit_price > 0 AND line_total > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE electronic_documents
    ADD COLUMN credit_note_id BIGINT NULL AFTER invoice_id,
    ADD CONSTRAINT fk_electronic_documents_credit_note FOREIGN KEY (credit_note_id) REFERENCES credit_notes (id),
    ADD CONSTRAINT uk_electronic_documents_credit_note UNIQUE (credit_note_id),
    ADD CONSTRAINT chk_electronic_documents_credit_note
        CHECK ((type = 'CREDIT_NOTE') = (credit_note_id IS NOT NULL));

ALTER TABLE billing_history.electronic_documents_aud
    ADD COLUMN credit_note_id BIGINT NULL;
