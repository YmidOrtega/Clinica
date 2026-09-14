CREATE TABLE clinical_ledger.chain_links (
    patient_uuid   CHAR(36)     NOT NULL,
    sequence       BIGINT       NOT NULL,
    entry_type     VARCHAR(20)  NOT NULL,
    entry_id       CHAR(36)     NOT NULL,
    format_version INT          NOT NULL,
    payload_hash   CHAR(64)     NOT NULL,
    previous_hash  CHAR(64)     NOT NULL,
    entry_hash     CHAR(64)     NOT NULL,
    key_id         VARCHAR(64)  NOT NULL,
    seal           VARCHAR(200) NOT NULL,
    sealed_at      DATETIME(6)  NOT NULL,

    CONSTRAINT pk_chain_links PRIMARY KEY (patient_uuid, sequence),
    CONSTRAINT uk_chain_links_entry UNIQUE (entry_type, entry_id),
    CONSTRAINT uk_chain_links_previous_hash UNIQUE (patient_uuid, previous_hash),
    CONSTRAINT fk_chain_links_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT chk_chain_links_sequence CHECK (sequence >= 1),
    CONSTRAINT chk_chain_links_entry_type
        CHECK (entry_type IN ('ENCOUNTER_OPENED', 'NOTE_SIGNED', 'NOTE_VOIDED', 'ENCOUNTER_CLOSED')),
    CONSTRAINT chk_chain_links_format_version CHECK (format_version = 1),
    CONSTRAINT chk_chain_links_hashes
        CHECK (REGEXP_LIKE(payload_hash, '^[0-9a-f]{64}$', 'c')
            AND REGEXP_LIKE(previous_hash, '^[0-9a-f]{64}$', 'c')
            AND REGEXP_LIKE(entry_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_chain_links_genesis
        CHECK ((sequence = 1) = (previous_hash = '0000000000000000000000000000000000000000000000000000000000000000'))
) ENGINE = InnoDB;
