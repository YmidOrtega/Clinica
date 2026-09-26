ALTER TABLE invoices
    ADD COLUMN purpose              VARCHAR(20)   NOT NULL DEFAULT 'SERVICES' AFTER uuid,
    ADD COLUMN shared_payment_kind  VARCHAR(30)   NULL AFTER patient_share_source,
    ADD COLUMN authorization_number VARCHAR(40)   NULL AFTER shared_payment_kind,
    ADD COLUMN collection_reference VARCHAR(38)   NULL AFTER authorization_number,
    ADD COLUMN expected_share       DECIMAL(14,2) NULL AFTER collection_reference,
    ADD CONSTRAINT uk_invoices_collection_reference UNIQUE (collection_reference),
    ADD CONSTRAINT chk_invoices_purpose CHECK (purpose IN ('SERVICES', 'SHARED_PAYMENT')),
    ADD CONSTRAINT chk_invoices_shared_payment
        CHECK ((purpose = 'SERVICES' AND shared_payment_kind IS NULL AND collection_reference IS NULL)
            OR (purpose = 'SHARED_PAYMENT' AND shared_payment_kind IN
                    ('COPAYMENT', 'MODERATING_FEE', 'RECOVERY_FEE', 'VOLUNTARY_PLAN')
                AND collection_reference IS NOT NULL AND buyer_kind = 'PATIENT' AND patient_share = 0)),
    ADD CONSTRAINT chk_invoices_expected_share CHECK (expected_share IS NULL OR expected_share >= 0);

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN purpose              VARCHAR(20)   NULL,
    ADD COLUMN shared_payment_kind  VARCHAR(30)   NULL,
    ADD COLUMN authorization_number VARCHAR(40)   NULL,
    ADD COLUMN collection_reference VARCHAR(38)   NULL,
    ADD COLUMN expected_share       DECIMAL(14,2) NULL;

CREATE INDEX idx_invoices_shared_payments ON invoices (account_id, purpose, status);

ALTER TABLE invoice_lines
    DROP CONSTRAINT chk_invoice_lines_kind,
    ADD CONSTRAINT chk_invoice_lines_kind
        CHECK ((kind = 'SERVICE' AND sale_number IS NOT NULL AND sale_line_uuid IS NOT NULL AND service_date IS NOT NULL)
            OR (kind IN ('PACKAGE', 'SHARED_PAYMENT') AND sale_line_uuid IS NULL));

CREATE TABLE invoice_shared_payments (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    invoice_id        BIGINT        NOT NULL,
    shared_invoice_id BIGINT        NOT NULL,

    CONSTRAINT pk_invoice_shared_payments PRIMARY KEY (id),
    CONSTRAINT uk_invoice_shared_payments UNIQUE (invoice_id, shared_invoice_id),
    CONSTRAINT fk_invoice_shared_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_invoice_shared_payments_shared FOREIGN KEY (shared_invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
