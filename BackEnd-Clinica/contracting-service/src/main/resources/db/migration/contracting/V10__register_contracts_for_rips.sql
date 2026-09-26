ALTER TABLE contracts
    ADD COLUMN coverage_plan VARCHAR(30) NULL AFTER modality,
    ADD COLUMN cucon         VARCHAR(64) NULL AFTER coverage_plan,
    ADD CONSTRAINT uk_contracts_cucon UNIQUE (cucon),
    ADD CONSTRAINT chk_contracts_cucon CHECK (cucon IS NULL OR REGEXP_LIKE(cucon, '^[0-9a-f]{64}$', 'c'));

ALTER TABLE contracting_history.contracts_aud
    ADD COLUMN coverage_plan VARCHAR(30) NULL,
    ADD COLUMN cucon         VARCHAR(64) NULL;

ALTER TABLE contracting_outbox.outbox_events DROP CHECK chk_outbox_events_type;

ALTER TABLE contracting_outbox.outbox_events ADD CONSTRAINT chk_outbox_events_type CHECK (type IN (
    'ContractDrafted', 'ContractTariffTermsAgreed', 'ContractRenamed', 'ContractValidityChanged',
    'ContractActivated', 'ContractSuspended', 'ContractTerminated', 'ContractTariffExceptionRegistered',
    'ContractTariffExceptionRevoked', 'ContractPackageAgreed', 'ContractPackageRevoked',
    'ContractAuthorizationRequired', 'ContractAuthorizationRequirementRevoked', 'ContractRipsRegistered',
    'TariffVersionPublished', 'TariffVersionRetired'));
