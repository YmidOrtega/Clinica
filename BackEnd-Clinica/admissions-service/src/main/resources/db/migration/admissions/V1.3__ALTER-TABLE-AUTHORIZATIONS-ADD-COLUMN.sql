ALTER TABLE authorizations
ADD COLUMN authorization_by VARCHAR(255) NOT NULL,
ADD COLUMN copayment_value DECIMAL(10, 2) NOT NULL;
