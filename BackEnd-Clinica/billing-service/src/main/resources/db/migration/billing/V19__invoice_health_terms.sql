ALTER TABLE invoices
    ADD COLUMN payment_modality  VARCHAR(20) NULL AFTER contract_number,
    ADD COLUMN coverage_plan     VARCHAR(30) NULL AFTER payment_modality,
    ADD COLUMN cucon             VARCHAR(64) NULL AFTER coverage_plan,
    ADD COLUMN uncontracted_care VARCHAR(40) NULL AFTER cucon,
    ADD COLUMN period_start      DATE        NULL AFTER issued_time,
    ADD COLUMN period_end        DATE        NULL AFTER period_start,
    ADD CONSTRAINT chk_invoices_payment_modality
        CHECK (payment_modality IS NULL OR payment_modality IN ('PACKAGE', 'GLOBAL_BUDGET', 'CAPITATION', 'EVENT')),
    ADD CONSTRAINT chk_invoices_health_terms
        CHECK ((payment_modality IS NULL AND coverage_plan IS NULL AND cucon IS NULL AND uncontracted_care IS NULL)
            OR (payment_modality IS NOT NULL AND coverage_plan IS NOT NULL
                AND (cucon IS NULL) <> (uncontracted_care IS NULL))),
    ADD CONSTRAINT chk_invoices_period CHECK (period_start IS NULL OR period_end >= period_start);

UPDATE invoices SET period_start = issued_on, period_end = issued_on WHERE issued_on IS NOT NULL;

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN payment_modality  VARCHAR(20) NULL,
    ADD COLUMN coverage_plan     VARCHAR(30) NULL,
    ADD COLUMN cucon             VARCHAR(64) NULL,
    ADD COLUMN uncontracted_care VARCHAR(40) NULL,
    ADD COLUMN period_start      DATE        NULL,
    ADD COLUMN period_end        DATE        NULL;
