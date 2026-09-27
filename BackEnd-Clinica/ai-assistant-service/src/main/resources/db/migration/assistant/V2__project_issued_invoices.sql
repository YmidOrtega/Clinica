CREATE TABLE assistant.invoices (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_uuid       UUID          NOT NULL,
    number             VARCHAR(24)   NOT NULL,
    purpose            VARCHAR(20)   NOT NULL,
    status             VARCHAR(10)   NOT NULL,
    issued_on          DATE          NOT NULL,
    admission_number   VARCHAR(40)   NOT NULL,
    buyer_kind         VARCHAR(10)   NOT NULL,
    payer_nit          VARCHAR(20)   NULL,
    contract_number    VARCHAR(40)   NULL,
    uncontracted_care  VARCHAR(40)   NULL,
    payable_total      NUMERIC(14,2) NOT NULL,
    credited_total     NUMERIC(14,2) NOT NULL,
    balance            NUMERIC(14,2) NOT NULL,
    share_shortfall    NUMERIC(14,2) NULL,
    dian_status        VARCHAR(20)   NULL,
    dian_status_at     TIMESTAMPTZ   NULL,
    cuv                VARCHAR(96)   NULL,
    filing_number      VARCHAR(60)   NULL,
    filed_on           DATE          NULL,
    last_event_type    VARCHAR(40)   NOT NULL,
    last_event_at      TIMESTAMPTZ   NOT NULL,
    state              JSONB         NOT NULL,
    CONSTRAINT uk_invoices_invoice_uuid UNIQUE (invoice_uuid),
    CONSTRAINT chk_invoices_purpose CHECK (purpose IN ('SERVICES', 'SHARED_PAYMENT')),
    CONSTRAINT chk_invoices_status CHECK (status IN ('ISSUED', 'VOIDED')),
    CONSTRAINT chk_invoices_buyer CHECK (buyer_kind IN ('PAYER', 'PATIENT'))
);

CREATE INDEX idx_invoices_issued_on ON assistant.invoices (issued_on DESC);
CREATE INDEX idx_invoices_admission ON assistant.invoices (admission_number);
CREATE INDEX idx_invoices_payer ON assistant.invoices (payer_nit, issued_on DESC);
