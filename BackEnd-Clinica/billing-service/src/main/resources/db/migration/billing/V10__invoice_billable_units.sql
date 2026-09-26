CREATE TABLE invoices (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    uuid                    CHAR(36)      NOT NULL,
    version                 BIGINT        NOT NULL,
    account_id              BIGINT        NOT NULL,
    unit_kind               VARCHAR(20)   NOT NULL,
    sale_uuid               CHAR(36)      NULL,
    unit_key                VARCHAR(40)   NOT NULL,
    buyer_kind              VARCHAR(20)   NOT NULL,
    buyer_reference         CHAR(36)      NOT NULL,
    buyer_document_type     VARCHAR(40)   NOT NULL,
    buyer_document_number   VARCHAR(20)   NOT NULL,
    buyer_name              VARCHAR(200)  NOT NULL,
    patient_uuid            CHAR(36)      NOT NULL,
    patient_document_type   VARCHAR(40)   NOT NULL,
    patient_document_number VARCHAR(20)   NOT NULL,
    patient_name            VARCHAR(200)  NOT NULL,
    patient_health_regime   VARCHAR(40)   NULL,
    contract_uuid           CHAR(36)      NULL,
    contract_number         VARCHAR(40)   NULL,
    gross_total             DECIMAL(14,2) NOT NULL,
    patient_share           DECIMAL(14,2) NOT NULL,
    payable_total           DECIMAL(14,2) NOT NULL,
    patient_share_source    VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    status_reason           VARCHAR(500)  NULL,
    status_changed_at       DATETIME(6)   NULL,
    prefix                  VARCHAR(4)    NULL,
    consecutive             BIGINT        NULL,
    number                  VARCHAR(24)   NULL,
    resolution_uuid         CHAR(36)      NULL,
    issued_on               DATE          NULL,
    live_slot               VARCHAR(40) GENERATED ALWAYS AS
                                (CASE WHEN status IN ('DRAFT', 'ISSUED') THEN unit_key END) STORED,
    created_at              DATETIME(6)   NOT NULL,
    created_by              VARCHAR(36)   NULL,
    updated_at              DATETIME(6)   NOT NULL,
    updated_by              VARCHAR(36)   NULL,

    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uk_invoices_uuid UNIQUE (uuid),
    CONSTRAINT uk_invoices_live_unit UNIQUE (live_slot),
    CONSTRAINT uk_invoices_number UNIQUE (number),
    CONSTRAINT uk_invoices_consecutive UNIQUE (resolution_uuid, consecutive),
    CONSTRAINT fk_invoices_account FOREIGN KEY (account_id) REFERENCES episode_accounts (id),

    CONSTRAINT chk_invoices_unit
        CHECK ((unit_kind = 'ACCOUNT' AND sale_uuid IS NULL) OR (unit_kind = 'SALE' AND sale_uuid IS NOT NULL)),
    CONSTRAINT chk_invoices_buyer CHECK (buyer_kind IN ('PAYER', 'PATIENT')),
    CONSTRAINT chk_invoices_totals
        CHECK (gross_total > 0 AND patient_share >= 0 AND payable_total >= 0
            AND payable_total = gross_total - patient_share
            AND (buyer_kind = 'PAYER' OR patient_share = 0)),
    CONSTRAINT chk_invoices_share_source CHECK (patient_share_source IN ('COPAYMENT', 'PRIVATE', 'ADJUSTED')),
    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'DISCARDED')),
    CONSTRAINT chk_invoices_numbering
        CHECK ((status = 'ISSUED' AND number IS NOT NULL AND consecutive IS NOT NULL AND resolution_uuid IS NOT NULL
                   AND issued_on IS NOT NULL AND status_changed_at IS NOT NULL)
            OR (status IN ('DRAFT', 'DISCARDED') AND number IS NULL AND consecutive IS NULL)),
    CONSTRAINT chk_invoices_discard
        CHECK (status <> 'DISCARDED' OR (CHAR_LENGTH(TRIM(status_reason)) >= 1 AND status_changed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_invoices_account ON invoices (account_id, created_at);

CREATE TABLE invoice_lines (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_id           BIGINT        NOT NULL,
    position             INT           NOT NULL,
    kind                 VARCHAR(20)   NOT NULL,
    sale_number          VARCHAR(20)   NULL,
    sale_line_uuid       CHAR(36)      NULL,
    code                 VARCHAR(40)   NOT NULL,
    description          VARCHAR(300)  NOT NULL,
    quantity             INT           NOT NULL,
    unit_price           DECIMAL(14,2) NOT NULL,
    line_total           DECIMAL(14,2) NOT NULL,
    price_origin         VARCHAR(30)   NULL,
    service_date         DATE          NULL,
    authorization_number VARCHAR(40)   NULL,

    CONSTRAINT pk_invoice_lines PRIMARY KEY (id),
    CONSTRAINT uk_invoice_lines_position UNIQUE (invoice_id, position),
    CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),

    CONSTRAINT chk_invoice_lines_kind
        CHECK ((kind = 'SERVICE' AND sale_number IS NOT NULL AND sale_line_uuid IS NOT NULL AND service_date IS NOT NULL)
            OR (kind = 'PACKAGE' AND sale_line_uuid IS NULL)),
    CONSTRAINT chk_invoice_lines_amounts CHECK (quantity >= 1 AND unit_price >= 0 AND line_total >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_history.invoices_aud (
    id                      BIGINT        NOT NULL,
    rev                     BIGINT        NOT NULL,
    revtype                 TINYINT       NOT NULL,
    uuid                    CHAR(36)      NULL,
    account_id              BIGINT        NULL,
    unit_kind               VARCHAR(20)   NULL,
    sale_uuid               CHAR(36)      NULL,
    unit_key                VARCHAR(40)   NULL,
    buyer_kind              VARCHAR(20)   NULL,
    buyer_reference         CHAR(36)      NULL,
    buyer_document_type     VARCHAR(40)   NULL,
    buyer_document_number   VARCHAR(20)   NULL,
    buyer_name              VARCHAR(200)  NULL,
    patient_uuid            CHAR(36)      NULL,
    patient_document_type   VARCHAR(40)   NULL,
    patient_document_number VARCHAR(20)   NULL,
    patient_name            VARCHAR(200)  NULL,
    patient_health_regime   VARCHAR(40)   NULL,
    contract_uuid           CHAR(36)      NULL,
    contract_number         VARCHAR(40)   NULL,
    gross_total             DECIMAL(14,2) NULL,
    patient_share           DECIMAL(14,2) NULL,
    payable_total           DECIMAL(14,2) NULL,
    patient_share_source    VARCHAR(20)   NULL,
    status                  VARCHAR(20)   NULL,
    status_reason           VARCHAR(500)  NULL,
    status_changed_at       DATETIME(6)   NULL,
    prefix                  VARCHAR(4)    NULL,
    consecutive             BIGINT        NULL,
    number                  VARCHAR(24)   NULL,
    resolution_uuid         CHAR(36)      NULL,
    issued_on               DATE          NULL,
    created_at              DATETIME(6)   NULL,
    created_by              VARCHAR(36)   NULL,
    updated_at              DATETIME(6)   NULL,
    updated_by              VARCHAR(36)   NULL,

    CONSTRAINT pk_invoices_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_invoices_aud_revision FOREIGN KEY (rev) REFERENCES billing_history.revisions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
