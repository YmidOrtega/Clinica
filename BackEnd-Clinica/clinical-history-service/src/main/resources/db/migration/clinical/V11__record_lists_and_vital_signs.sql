ALTER TABLE clinical_workspace.note_drafts
    ADD COLUMN updates_ciphertext MEDIUMBLOB NULL AFTER content_ciphertext;

CREATE TABLE clinical_ledger.list_item_events (
    id                 CHAR(36)        NOT NULL,
    item_id            CHAR(36)        NOT NULL,
    patient_uuid       CHAR(36)        NOT NULL,
    category           VARCHAR(30)     NOT NULL,
    event_type         VARCHAR(20)     NOT NULL,
    status             VARCHAR(20)     NOT NULL,
    payload_key_id     CHAR(36)        NOT NULL,
    payload_ciphertext VARBINARY(8000) NOT NULL,
    note_id            CHAR(36)        NOT NULL,

    CONSTRAINT pk_list_item_events PRIMARY KEY (id),
    CONSTRAINT fk_list_item_events_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT fk_list_item_events_note FOREIGN KEY (note_id) REFERENCES clinical_ledger.notes (id),
    CONSTRAINT fk_list_item_events_payload_key FOREIGN KEY (payload_key_id) REFERENCES clinical_keys.data_keys (id),
    CONSTRAINT chk_list_item_events_category
        CHECK (category IN ('ALLERGY', 'CHRONIC_CONDITION', 'CURRENT_MEDICATION', 'FAMILY_HISTORY', 'PAST_HISTORY', 'VACCINATION')),
    CONSTRAINT chk_list_item_events_type CHECK (event_type IN ('ADDED', 'STATUS_CHANGED')),
    CONSTRAINT chk_list_item_events_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RESOLVED', 'ENTERED_IN_ERROR')),
    CONSTRAINT chk_list_item_events_added
        CHECK ((event_type = 'ADDED') = (id = item_id) AND (event_type <> 'ADDED' OR status = 'ACTIVE'))
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_list_item_events_patient ON clinical_ledger.list_item_events (patient_uuid, item_id);
CREATE INDEX idx_list_item_events_note ON clinical_ledger.list_item_events (note_id);

CREATE TABLE clinical_ledger.vital_sign_observations (
    id           CHAR(36)     NOT NULL,
    patient_uuid CHAR(36)     NOT NULL,
    note_id      CHAR(36)     NOT NULL,
    kind         VARCHAR(30)  NOT NULL,
    value        DECIMAL(7,2) NOT NULL,
    measured_at  DATETIME(6)  NOT NULL,

    CONSTRAINT pk_vital_sign_observations PRIMARY KEY (id),
    CONSTRAINT uk_vital_sign_observations_reading UNIQUE (note_id, kind, measured_at),
    CONSTRAINT fk_vital_sign_observations_patient FOREIGN KEY (patient_uuid)
        REFERENCES `${flyway:defaultSchema}`.patient_references (uuid),
    CONSTRAINT fk_vital_sign_observations_note FOREIGN KEY (note_id) REFERENCES clinical_ledger.notes (id),
    CONSTRAINT chk_vital_sign_observations_kind
        CHECK (kind IN ('HEART_RATE', 'RESPIRATORY_RATE', 'SYSTOLIC_BLOOD_PRESSURE', 'DIASTOLIC_BLOOD_PRESSURE', 'TEMPERATURE',
                        'OXYGEN_SATURATION', 'WEIGHT', 'HEIGHT', 'GLASGOW_COMA_SCALE', 'PAIN_SCALE')),
    CONSTRAINT chk_vital_sign_observations_value CHECK (value >= 0)
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_vital_sign_observations_series ON clinical_ledger.vital_sign_observations (patient_uuid, kind, measured_at);
