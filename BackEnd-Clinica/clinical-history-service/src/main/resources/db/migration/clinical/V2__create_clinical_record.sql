CREATE DATABASE IF NOT EXISTS clinical_ledger
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE DATABASE IF NOT EXISTS clinical_workspace
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE clinical_ledger.encounters (
    id             CHAR(36)    NOT NULL,
    patient_uuid   CHAR(36)    NOT NULL,
    type           VARCHAR(20) NOT NULL,
    admission_id   VARCHAR(64) NULL,
    opened_at      DATETIME(6) NOT NULL,
    opened_by      CHAR(36)    NOT NULL,
    opened_by_role VARCHAR(10) NOT NULL,

    CONSTRAINT pk_encounters PRIMARY KEY (id),
    CONSTRAINT fk_encounters_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT chk_encounters_id
        CHECK (REGEXP_LIKE(id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_encounters_type CHECK (type IN ('OUTPATIENT', 'EMERGENCY', 'INPATIENT', 'TELEHEALTH')),
    CONSTRAINT chk_encounters_opened_by_role CHECK (opened_by_role IN ('DOCTOR', 'NURSE'))
) ENGINE = InnoDB;

CREATE INDEX idx_encounters_patient_opened_at ON clinical_ledger.encounters (patient_uuid, opened_at);

CREATE TABLE clinical_ledger.encounter_closures (
    encounter_id   CHAR(36)    NOT NULL,
    closed_at      DATETIME(6) NOT NULL,
    closed_by      CHAR(36)    NOT NULL,
    closed_by_role VARCHAR(10) NOT NULL,

    CONSTRAINT pk_encounter_closures PRIMARY KEY (encounter_id),
    CONSTRAINT fk_encounter_closures_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_ledger.encounters (id),
    CONSTRAINT chk_encounter_closures_role CHECK (closed_by_role IN ('DOCTOR', 'NURSE'))
) ENGINE = InnoDB;

CREATE TABLE clinical_ledger.notes (
    id             CHAR(36)    NOT NULL,
    encounter_id   CHAR(36)    NOT NULL,
    type           VARCHAR(20) NOT NULL,
    content        JSON        NOT NULL,
    amends_note_id CHAR(36)    NULL,
    author_uuid    CHAR(36)    NOT NULL,
    author_role    VARCHAR(10) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    recorded_at    DATETIME(6) NOT NULL,
    extemporaneous BOOLEAN     NOT NULL,

    CONSTRAINT pk_notes PRIMARY KEY (id),
    CONSTRAINT fk_notes_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_ledger.encounters (id),
    CONSTRAINT fk_notes_amended_note FOREIGN KEY (amends_note_id) REFERENCES clinical_ledger.notes (id),
    CONSTRAINT chk_notes_id
        CHECK (REGEXP_LIKE(id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$', 'c')),
    CONSTRAINT chk_notes_type
        CHECK (type IN ('ADMISSION', 'PROGRESS', 'TRIAGE', 'CONSULTATION', 'NURSING', 'DISCHARGE', 'ADDENDUM')),
    CONSTRAINT chk_notes_content_type CHECK (JSON_UNQUOTE(JSON_EXTRACT(content, '$.type')) = type),
    CONSTRAINT chk_notes_amendment CHECK ((type = 'ADDENDUM') = (amends_note_id IS NOT NULL)),
    CONSTRAINT chk_notes_author_role CHECK (author_role IN ('DOCTOR', 'NURSE'))
) ENGINE = InnoDB;

CREATE INDEX idx_notes_encounter_recorded_at ON clinical_ledger.notes (encounter_id, recorded_at);

CREATE TABLE clinical_ledger.note_voids (
    note_id        CHAR(36)     NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    voided_by      CHAR(36)     NOT NULL,
    voided_by_role VARCHAR(10)  NOT NULL,
    voided_at      DATETIME(6)  NOT NULL,

    CONSTRAINT pk_note_voids PRIMARY KEY (note_id),
    CONSTRAINT fk_note_voids_note FOREIGN KEY (note_id) REFERENCES clinical_ledger.notes (id),
    CONSTRAINT chk_note_voids_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT chk_note_voids_role CHECK (voided_by_role IN ('DOCTOR', 'NURSE'))
) ENGINE = InnoDB;

CREATE TABLE clinical_workspace.note_drafts (
    id             CHAR(36)    NOT NULL,
    encounter_id   CHAR(36)    NOT NULL,
    type           VARCHAR(20) NOT NULL,
    content        JSON        NOT NULL,
    author_uuid    CHAR(36)    NOT NULL,
    author_role    VARCHAR(10) NOT NULL,
    occurred_at    DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,

    CONSTRAINT pk_note_drafts PRIMARY KEY (id),
    CONSTRAINT fk_note_drafts_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_ledger.encounters (id),
    CONSTRAINT chk_note_drafts_type
        CHECK (type IN ('ADMISSION', 'PROGRESS', 'TRIAGE', 'CONSULTATION', 'NURSING', 'DISCHARGE', 'ADDENDUM')),
    CONSTRAINT chk_note_drafts_content_type CHECK (JSON_UNQUOTE(JSON_EXTRACT(content, '$.type')) = type),
    CONSTRAINT chk_note_drafts_author_role CHECK (author_role IN ('DOCTOR', 'NURSE')),
    CONSTRAINT chk_note_drafts_version CHECK (version >= 0)
) ENGINE = InnoDB;

CREATE INDEX idx_note_drafts_author_updated_at ON clinical_workspace.note_drafts (author_uuid, updated_at);
