ALTER TABLE invoices
    ADD COLUMN uncontracted_justification VARCHAR(500) NULL AFTER uncontracted_care,
    ADD COLUMN policy_number              VARCHAR(30)  NULL AFTER uncontracted_justification,
    ADD CONSTRAINT chk_invoices_uncontracted_buyer
        CHECK (uncontracted_care IS NULL OR (uncontracted_care = 'PRIVATE_PATIENT') = (buyer_kind = 'PATIENT')),
    ADD CONSTRAINT chk_invoices_uncontracted_justification
        CHECK ((uncontracted_justification IS NOT NULL)
            = (uncontracted_care IS NOT NULL AND uncontracted_care <> 'PRIVATE_PATIENT')),
    ADD CONSTRAINT chk_invoices_policy_number
        CHECK (coverage_plan IS NULL
            OR (coverage_plan IN ('SOAT_POLICY', 'COMPLEMENTARY_PLAN', 'PREPAID_MEDICINE', 'HEALTH_POLICY'))
                = (policy_number IS NOT NULL));

ALTER TABLE billing_history.invoices_aud
    ADD COLUMN uncontracted_justification VARCHAR(500) NULL,
    ADD COLUMN policy_number              VARCHAR(30)  NULL;
