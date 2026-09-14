ALTER TABLE clinical_ledger.notes
    ADD COLUMN author_email VARCHAR(254) NOT NULL AFTER author_role;
