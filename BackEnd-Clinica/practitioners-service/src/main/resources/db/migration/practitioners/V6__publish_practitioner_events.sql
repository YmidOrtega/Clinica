CREATE DATABASE IF NOT EXISTS practitioners_outbox
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE practitioners_outbox.outbox_events (
    id            CHAR(36)    NOT NULL,
    aggregatetype VARCHAR(50) NOT NULL,
    aggregateid   CHAR(36)    NOT NULL,
    type          VARCHAR(80) NOT NULL,
    payload       JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT chk_outbox_events_aggregatetype CHECK (aggregatetype = 'practitioners'),
    CONSTRAINT chk_outbox_events_type CHECK (type IN (
        'PractitionerRegistered', 'PractitionerIdentityCorrected', 'PractitionerRegistrationCorrected',
        'PractitionerContactUpdated', 'PractitionerRelationshipAgreed', 'PractitionerSpecialtiesAssigned',
        'PractitionerSuspended', 'PractitionerRetired', 'PractitionerReinstated',
        'PractitionerAccountLinked', 'PractitionerAccountUnlinked'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_outbox_events_created_at ON practitioners_outbox.outbox_events (created_at);
