ALTER TABLE invoices
    DROP CONSTRAINT chk_invoices_shared_payment,
    ADD CONSTRAINT chk_invoices_shared_payment
        CHECK ((purpose = 'SERVICES' AND shared_payment_kind IS NULL AND collection_reference IS NULL)
            OR (purpose = 'SHARED_PAYMENT' AND shared_payment_kind IN ('COPAYMENT', 'MODERATING_FEE', 'VOLUNTARY_PLAN')
                AND collection_reference IS NOT NULL AND buyer_kind = 'PATIENT' AND patient_share = 0));
