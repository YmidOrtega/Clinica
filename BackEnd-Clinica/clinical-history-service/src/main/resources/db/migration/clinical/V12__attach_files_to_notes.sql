CREATE TABLE clinical_workspace.draft_attachments (
    id              CHAR(36)       NOT NULL,
    draft_id        CHAR(36)       NOT NULL,
    media_type      VARCHAR(10)    NOT NULL,
    size_bytes      BIGINT         NOT NULL,
    sha256          CHAR(64)       NOT NULL,
    name_key_id     CHAR(36)       NOT NULL,
    name_ciphertext VARBINARY(600) NOT NULL,
    uploaded_at     DATETIME(6)    NOT NULL,

    CONSTRAINT pk_draft_attachments PRIMARY KEY (id),
    CONSTRAINT fk_draft_attachments_draft FOREIGN KEY (draft_id) REFERENCES clinical_workspace.note_drafts (id) ON DELETE CASCADE,
    CONSTRAINT fk_draft_attachments_name_key FOREIGN KEY (name_key_id) REFERENCES clinical_keys.data_keys (id),
    CONSTRAINT chk_draft_attachments_media_type CHECK (media_type IN ('PDF', 'JPEG', 'PNG')),
    CONSTRAINT chk_draft_attachments_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT chk_draft_attachments_sha256 CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_draft_attachments_draft ON clinical_workspace.draft_attachments (draft_id);

CREATE TABLE clinical_ledger.note_attachments (
    id              CHAR(36)       NOT NULL,
    note_id         CHAR(36)       NOT NULL,
    patient_uuid    CHAR(36)       NOT NULL,
    media_type      VARCHAR(10)    NOT NULL,
    size_bytes      BIGINT         NOT NULL,
    sha256          CHAR(64)       NOT NULL,
    name_key_id     CHAR(36)       NOT NULL,
    name_ciphertext VARBINARY(600) NOT NULL,

    CONSTRAINT pk_note_attachments PRIMARY KEY (id),
    CONSTRAINT fk_note_attachments_note FOREIGN KEY (note_id) REFERENCES clinical_ledger.notes (id),
    CONSTRAINT fk_note_attachments_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT fk_note_attachments_name_key FOREIGN KEY (name_key_id) REFERENCES clinical_keys.data_keys (id),
    CONSTRAINT chk_note_attachments_media_type CHECK (media_type IN ('PDF', 'JPEG', 'PNG')),
    CONSTRAINT chk_note_attachments_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT chk_note_attachments_sha256 CHECK (REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c'))
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_note_attachments_note ON clinical_ledger.note_attachments (note_id);
CREATE INDEX idx_note_attachments_patient ON clinical_ledger.note_attachments (patient_uuid);

CREATE TABLE scheduled_job_runs (
    name              VARCHAR(60) NOT NULL,
    last_completed_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_scheduled_job_runs PRIMARY KEY (name)
) ENGINE = InnoDB ENCRYPTION = 'Y';
