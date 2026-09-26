CREATE TABLE clinical_encounters (
    encounter_id   CHAR(36)    NOT NULL,
    admission_uuid CHAR(36)    NULL,
    patient_uuid   CHAR(36)    NOT NULL,
    encounter_type VARCHAR(20) NOT NULL,
    opened_at      DATETIME(6) NOT NULL,
    service_code   VARCHAR(10) NULL,
    modality       VARCHAR(2)  NULL,
    service_group  VARCHAR(2)  NULL,
    closed_at      DATETIME(6) NULL,

    CONSTRAINT pk_clinical_encounters PRIMARY KEY (encounter_id),
    INDEX idx_clinical_encounters_admission (admission_uuid, opened_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE clinical_notes (
    note_id          CHAR(36)    NOT NULL,
    encounter_id     CHAR(36)    NOT NULL,
    admission_uuid   CHAR(36)    NULL,
    note_type        VARCHAR(20) NOT NULL,
    care_occurred_at DATETIME(6) NOT NULL,
    purpose          VARCHAR(2)  NULL,
    cause            VARCHAR(2)  NULL,
    voided           BOOLEAN     NOT NULL,

    CONSTRAINT pk_clinical_notes PRIMARY KEY (note_id),
    INDEX idx_clinical_notes_admission (admission_uuid, care_occurred_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE clinical_note_diagnoses (
    note_id  CHAR(36)    NOT NULL,
    position INT         NOT NULL,
    code     VARCHAR(4)  NOT NULL,
    role     VARCHAR(10) NOT NULL,
    type     VARCHAR(20) NOT NULL,

    CONSTRAINT pk_clinical_note_diagnoses PRIMARY KEY (note_id, position),
    CONSTRAINT fk_clinical_note_diagnoses_note FOREIGN KEY (note_id) REFERENCES clinical_notes (note_id),
    CONSTRAINT chk_clinical_note_diagnoses_role CHECK (role IN ('PRINCIPAL', 'RELATED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE invoice_lines
    ADD COLUMN line_origin VARCHAR(20) NULL AFTER kind,
    ADD CONSTRAINT chk_invoice_lines_origin CHECK (line_origin IS NULL OR line_origin IN ('MANUAL', 'STAY', 'AUTHORIZED'));
