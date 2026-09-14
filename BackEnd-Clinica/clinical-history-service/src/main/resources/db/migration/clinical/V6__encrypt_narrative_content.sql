CREATE DATABASE IF NOT EXISTS clinical_keys
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci
    DEFAULT ENCRYPTION = 'Y';

CREATE TABLE clinical_keys.data_keys (
    id           CHAR(36)    NOT NULL,
    patient_uuid CHAR(36)    NOT NULL,
    created_at   DATETIME(6) NOT NULL,

    CONSTRAINT pk_data_keys PRIMARY KEY (id),
    CONSTRAINT uk_data_keys_patient UNIQUE (patient_uuid)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE TABLE clinical_keys.data_key_wrappings (
    data_key_id   CHAR(36)      NOT NULL,
    master_key_id VARCHAR(64)   NOT NULL,
    wrapped_key   VARBINARY(64) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,

    CONSTRAINT pk_data_key_wrappings PRIMARY KEY (data_key_id, master_key_id),
    CONSTRAINT fk_data_key_wrappings_data_key FOREIGN KEY (data_key_id) REFERENCES clinical_keys.data_keys (id),
    CONSTRAINT chk_data_key_wrappings_length CHECK (LENGTH(wrapped_key) = 61)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_data_key_wrappings_master_key ON clinical_keys.data_key_wrappings (master_key_id);

ALTER TABLE clinical_ledger.notes
    DROP CHECK chk_notes_content_type,
    DROP COLUMN content,
    ADD COLUMN content_key_id CHAR(36) NOT NULL AFTER type,
    ADD COLUMN content_ciphertext MEDIUMBLOB NOT NULL AFTER content_key_id,
    ADD CONSTRAINT fk_notes_content_key FOREIGN KEY (content_key_id) REFERENCES clinical_keys.data_keys (id);

ALTER TABLE clinical_ledger.note_voids
    DROP CHECK chk_note_voids_reason,
    DROP COLUMN reason,
    ADD COLUMN reason_key_id CHAR(36) NOT NULL AFTER note_id,
    ADD COLUMN reason_ciphertext VARBINARY(2100) NOT NULL AFTER reason_key_id,
    ADD CONSTRAINT fk_note_voids_reason_key FOREIGN KEY (reason_key_id) REFERENCES clinical_keys.data_keys (id);

ALTER TABLE clinical_workspace.note_drafts
    DROP CHECK chk_note_drafts_content_type,
    DROP COLUMN content,
    ADD COLUMN content_key_id CHAR(36) NOT NULL AFTER type,
    ADD COLUMN content_ciphertext MEDIUMBLOB NOT NULL AFTER content_key_id,
    ADD CONSTRAINT fk_note_drafts_content_key FOREIGN KEY (content_key_id) REFERENCES clinical_keys.data_keys (id);
