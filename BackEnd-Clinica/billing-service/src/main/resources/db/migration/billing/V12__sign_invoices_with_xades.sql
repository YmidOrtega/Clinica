ALTER TABLE invoices
    ADD COLUMN signed_at DATETIME(6) NULL AFTER qr_content,
    ADD CONSTRAINT chk_invoices_signed CHECK (signed_at IS NULL OR status = 'ISSUED'),
    ADD INDEX idx_invoices_awaiting_signature (status, signed_at, status_changed_at);

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN signed_at DATETIME(6) NULL;

ALTER TABLE invoice_documents
    DROP CONSTRAINT chk_invoice_documents_kind,
    ADD CONSTRAINT chk_invoice_documents_kind CHECK (kind IN ('UBL_UNSIGNED', 'UBL_SIGNED'));
