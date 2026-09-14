CREATE TABLE clinical_ledger.record_copies (
    id                CHAR(36)        NOT NULL,
    patient_uuid      CHAR(36)        NOT NULL,
    requested_by      CHAR(36)        NOT NULL,
    requested_role    VARCHAR(30)     NOT NULL,
    reason_key_id     CHAR(36)        NOT NULL,
    reason_ciphertext VARBINARY(2100) NOT NULL,
    period_from       DATETIME(6)     NULL,
    period_to         DATETIME(6)     NULL,
    entries           INT             NOT NULL,
    chain_verified    BOOLEAN         NOT NULL,
    document_sha256   CHAR(64)        NOT NULL,
    key_id            VARCHAR(64)     NOT NULL,
    seal              VARCHAR(200)    NOT NULL,
    generated_at      DATETIME(6)     NOT NULL,

    CONSTRAINT pk_record_copies PRIMARY KEY (id),
    CONSTRAINT fk_record_copies_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT fk_record_copies_reason_key FOREIGN KEY (reason_key_id) REFERENCES clinical_keys.data_keys (id),
    CONSTRAINT chk_record_copies_sha256 CHECK (REGEXP_LIKE(document_sha256, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_record_copies_period CHECK (period_from IS NULL OR period_to IS NULL OR period_from < period_to)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_record_copies_patient ON clinical_ledger.record_copies (patient_uuid, generated_at);
