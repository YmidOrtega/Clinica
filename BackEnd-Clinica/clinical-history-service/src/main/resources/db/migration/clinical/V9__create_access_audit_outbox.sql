CREATE DATABASE IF NOT EXISTS clinical_outbox
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci
    DEFAULT ENCRYPTION = 'Y';

CREATE TABLE clinical_outbox.outbox_events (
    id            CHAR(36)    NOT NULL,
    aggregatetype VARCHAR(50) NOT NULL,
    aggregateid   CHAR(36)    NOT NULL,
    type          VARCHAR(80) NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype IN ('clinical.access-audit')),
    CONSTRAINT chk_outbox_events_type CHECK (type IN ('ClinicalRecordAccessed', 'ClinicalRecordAccessDenied'))
) ENGINE = InnoDB ENCRYPTION = 'Y';

CREATE INDEX idx_outbox_events_created_at ON clinical_outbox.outbox_events (created_at);
