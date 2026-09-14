CREATE DATABASE IF NOT EXISTS patient_outbox
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE patient_outbox.outbox_events (
    id            CHAR(36)    NOT NULL,
    aggregatetype VARCHAR(50) NOT NULL,
    aggregateid   CHAR(36)    NOT NULL,
    type          VARCHAR(80) NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype = 'patient'),
    CONSTRAINT chk_outbox_events_type CHECK (type IN ('PatientRegistered', 'PatientDocumentChanged',
                                                       'PatientDemographicsCorrected', 'PatientAffiliationUpdated',
                                                       'PatientDeactivated', 'PatientReactivated', 'PatientDied'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_outbox_events_created_at ON patient_outbox.outbox_events (created_at);
