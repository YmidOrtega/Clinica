CREATE TABLE clinical_ledger.care_team_members (
    encounter_id   CHAR(36)    NOT NULL,
    clinician_uuid CHAR(36)    NOT NULL,
    clinician_role VARCHAR(10) NOT NULL,
    added_by       CHAR(36)    NOT NULL,
    added_at       DATETIME(6) NOT NULL,

    CONSTRAINT pk_care_team_members PRIMARY KEY (encounter_id, clinician_uuid),
    CONSTRAINT fk_care_team_members_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_ledger.encounters (id),
    CONSTRAINT chk_care_team_members_role CHECK (clinician_role IN ('DOCTOR', 'NURSE'))
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_care_team_members_clinician ON clinical_ledger.care_team_members (clinician_uuid);

INSERT INTO clinical_ledger.care_team_members (encounter_id, clinician_uuid, clinician_role, added_by, added_at)
SELECT id, opened_by, opened_by_role, opened_by, opened_at FROM clinical_ledger.encounters;

CREATE TABLE clinical_ledger.emergency_accesses (
    id                CHAR(36)        NOT NULL,
    patient_uuid      CHAR(36)        NOT NULL,
    clinician_uuid    CHAR(36)        NOT NULL,
    clinician_role    VARCHAR(10)     NOT NULL,
    reason_key_id     CHAR(36)        NOT NULL,
    reason_ciphertext VARBINARY(2100) NOT NULL,
    granted_at        DATETIME(6)     NOT NULL,
    expires_at        DATETIME(6)     NOT NULL,

    CONSTRAINT pk_emergency_accesses PRIMARY KEY (id),
    CONSTRAINT fk_emergency_accesses_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT fk_emergency_accesses_reason_key FOREIGN KEY (reason_key_id) REFERENCES clinical_keys.data_keys (id),
    CONSTRAINT chk_emergency_accesses_role CHECK (clinician_role IN ('DOCTOR', 'NURSE')),
    CONSTRAINT chk_emergency_accesses_window CHECK (expires_at > granted_at)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_emergency_accesses_clinician ON clinical_ledger.emergency_accesses (clinician_uuid, expires_at);
